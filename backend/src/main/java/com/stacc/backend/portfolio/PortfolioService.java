package com.stacc.backend.portfolio;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.documents.DocumentVaultService;
import com.stacc.backend.documents.VaultDocument;
import com.stacc.backend.engagement.EngagementRecordQueries;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portfolios (v0.12.9). The records section is read live on every request from the owner's college-issued documents
 * (not withdrawn) and campus participation; nothing is copied. Self-entered items belong to their owner, who alone reads
 * the portfolio; a named faculty member sees only the items waiting for their verification. Every change locks the
 * item row.
 */
@Service
@Transactional
public class PortfolioService {

    static final String NOT_FOUND = "Portfolio item not found.";
    static final String NOT_FACULTY = "Ask an active faculty account to verify it.";

    public record RecordView(String source, String title, String detail, Instant at) {
    }

    public record ItemView(Long itemId, PortfolioItem.Kind kind, String title, String description, LocalDate occurredOn, String link,
            PortfolioItem.Verification verification, String verifierLoginId, String verificationNote, Instant decidedAt) {
    }

    public record Portfolio(List<RecordView> records, List<ItemView> items) {
    }

    public record WaitingItem(Long itemId, String ownerLoginId, PortfolioItem.Kind kind, String title, String description, LocalDate occurredOn,
            String link, Instant requestedAt) {
    }

    public record ItemInput(PortfolioItem.Kind kind, String title, String description, LocalDate occurredOn, String link) {
    }

    private final PortfolioItemRepository items;
    private final DocumentVaultService documents;
    private final EngagementRecordQueries engagement;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public PortfolioService(PortfolioItemRepository items, DocumentVaultService documents, EngagementRecordQueries engagement,
            UserAccountRepository accounts, Clock clock) {
        this.items = items;
        this.documents = documents;
        this.engagement = engagement;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Portfolio mine(Long accountId) {
        List<RecordView> records = new ArrayList<>();
        documents.mine(accountId).stream()
                .filter(document -> document.origin() == VaultDocument.Origin.ISSUED && !document.withdrawn())
                .forEach(document -> records.add(new RecordView("DOCUMENT", document.title(), "Issued by the college", document.createdAt())));
        engagement.participation(accountId)
                .forEach(found -> records.add(new RecordView(found.kind().name(), found.title(), found.detail(), found.at())));
        records.sort(Comparator.comparing(RecordView::at, Comparator.nullsLast(Comparator.reverseOrder())));
        return new Portfolio(records, items.findVisibleForOwner(accountId).stream().map(PortfolioService::view).toList());
    }

    public ItemView add(Long accountId, ItemInput input) {
        PortfolioItem saved = items.saveAndFlush(valid(() -> new PortfolioItem(accounts.getReferenceById(accountId), input.kind(), input.title(),
                input.description(), input.occurredOn(), input.link(), now())));
        return view(saved);
    }

    public ItemView edit(Long accountId, Long itemId, ItemInput input) {
        PortfolioItem item = own(accountId, itemId);
        valid(() -> {
            item.edit(input.kind(), input.title(), input.description(), input.occurredOn(), input.link());
            return item;
        });
        items.flush();
        return view(item);
    }

    public ItemView hide(Long accountId, Long itemId) {
        PortfolioItem item = own(accountId, itemId);
        item.hide(now());
        items.flush();
        return view(item);
    }

    /** Sends the item to one active faculty member, who is not the owner, for verification. */
    public ItemView requestVerification(Long accountId, Long itemId, String verifierLoginId) {
        UserAccount faculty = (verifierLoginId == null ? Optional.<UserAccount>empty()
                : accounts.findWithRolesAndPermissionsByLoginId(verifierLoginId.strip()))
                .filter(found -> found.getStatus() == AccountStatus.ACTIVE && found.hasRole(RoleName.FACULTY) && !found.getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_FACULTY));
        PortfolioItem item = own(accountId, itemId);
        valid(() -> {
            item.requestVerification(faculty, now());
            return item;
        });
        items.flush();
        return view(item);
    }

    @Transactional(readOnly = true)
    public List<WaitingItem> waitingFor(Long accountId) {
        return items.findWaitingFor(accountId).stream().map(item -> new WaitingItem(item.getId(), item.getOwner().getLoginId(), item.getKind(),
                item.getTitle(), item.getDescription(), item.getOccurredOn(), item.getLink(), item.getRequestedAt())).toList();
    }

    /** The named faculty member verifies or declines, once. Anyone else gets the same 404. */
    public void decide(Long accountId, Long itemId, boolean verify, String note) {
        PortfolioItem item = (itemId == null ? Optional.<PortfolioItem>empty() : items.findByIdForUpdate(itemId))
                .filter(found -> found.getVerifier() != null && found.getVerifier().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        valid(() -> {
            item.decide(verify, note, now());
            return item;
        });
        items.flush();
    }

    private PortfolioItem own(Long accountId, Long itemId) {
        return (itemId == null ? Optional.<PortfolioItem>empty() : items.findByIdForUpdate(itemId))
                .filter(found -> found.getOwner().getId().equals(accountId) && found.getHiddenAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private static ItemView view(PortfolioItem item) {
        return new ItemView(item.getId(), item.getKind(), item.getTitle(), item.getDescription(), item.getOccurredOn(), item.getLink(),
                item.getVerification(), item.getVerifier() == null ? null : item.getVerifier().getLoginId(), item.getVerificationNote(),
                item.getDecidedAt());
    }

    private static <T> T valid(Supplier<T> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (IllegalStateException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refused.getMessage());
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
