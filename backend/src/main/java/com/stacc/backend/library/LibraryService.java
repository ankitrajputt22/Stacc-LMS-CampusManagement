package com.stacc.backend.library;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The library (v0.12.13). Members (students and faculty) search the catalog, see their loans and holds, place and
 * cancel holds, and renew a loan once. Librarians keep the catalog and resources and lend and take back copies.
 * Lending locks the borrower's account row, then the copy row, then reads the borrower's loans and the title's copies,
 * loans, and holds with locking reads: one open loan per copy, at most five per borrower, and holds first in queue
 * order. Overdue and queue positions are worked out on read.
 */
@Service
@Transactional
public class LibraryService {

    public static final int LOAN_LIMIT = 5;
    static final String NOT_FOUND = "Not found in the library.";

    public record TitleView(Long titleId, String title, String authors, String isbn, String publisher, Integer publishedYear, String subject,
            boolean active, int copies, int available, int holdsWaiting, Integer myHoldPosition) {
    }

    public record LoanView(Long loanId, String title, String barcode, Instant issuedAt, Instant dueAt, boolean renewed, boolean overdue) {
    }

    public record HoldView(Long holdId, Long titleId, String title, int position) {
    }

    public record MyLibrary(List<LoanView> loans, List<HoldView> holds) {
    }

    public record ResourceView(Long resourceId, String title, String description, String url, boolean active) {
    }

    public record CopyView(Long copyId, String barcode, String shelf, boolean withdrawn, String borrowerLoginId, Instant dueAt) {
    }

    public record DeskLoan(Long loanId, String borrowerLoginId, String title, String barcode, Instant dueAt, boolean overdue) {
    }

    private final LibraryTitleRepository titles;
    private final LibraryCopyRepository copies;
    private final LibraryLoanRepository loans;
    private final LibraryHoldRepository holds;
    private final LibraryResourceRepository resources;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public LibraryService(LibraryTitleRepository titles, LibraryCopyRepository copies, LibraryLoanRepository loans, LibraryHoldRepository holds,
            LibraryResourceRepository resources, UserAccountRepository accounts, Clock clock) {
        this.titles = titles;
        this.copies = copies;
        this.loans = loans;
        this.holds = holds;
        this.resources = resources;
        this.accounts = accounts;
        this.clock = clock;
    }

    // --- Members

    /** Up to 50 titles matching the text (title, authors, subject, or ISBN), with copies, availability, and the queue. */
    @Transactional(readOnly = true)
    public List<TitleView> search(Long accountId, String text, boolean includeInactive) {
        String query = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        if (query.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Search with at most 100 characters.");
        }
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        List<LibraryTitle> found = titles.search(pattern, includeInactive, PageRequest.of(0, 50));
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(LibraryTitle::getId).toList();
        List<LibraryCopy> allCopies = copies.findForTitles(ids);
        Set<Long> onLoan = allCopies.isEmpty() ? Set.of()
                : loans.findOpenForCopies(allCopies.stream().map(LibraryCopy::getId).toList()).stream().map(l -> l.getCopy().getId()).collect(Collectors.toSet());
        Map<Long, List<LibraryCopy>> byTitle = allCopies.stream().filter(c -> c.getWithdrawnAt() == null).collect(Collectors.groupingBy(c -> c.getTitle().getId()));
        Map<Long, List<LibraryHold>> queue = holds.findActiveForTitles(ids).stream().collect(Collectors.groupingBy(h -> h.getTitle().getId()));
        return found.stream().map(title -> {
            List<LibraryCopy> its = byTitle.getOrDefault(title.getId(), List.of());
            List<LibraryHold> waiting = queue.getOrDefault(title.getId(), List.of());
            Integer position = null;
            for (int index = 0; index < waiting.size(); index++) {
                if (waiting.get(index).getAccount().getId().equals(accountId)) {
                    position = index + 1;
                }
            }
            return new TitleView(title.getId(), title.getTitle(), title.getAuthors(), title.getIsbn(), title.getPublisher(), title.getPublishedYear(),
                    title.getSubject(), title.isActive(), its.size(), (int) its.stream().filter(c -> !onLoan.contains(c.getId())).count(), waiting.size(), position);
        }).toList();
    }

    @Transactional(readOnly = true)
    public MyLibrary mine(Long accountId) {
        Instant now = now();
        List<LoanView> open = loans.findOpenForBorrower(accountId).stream().map(loan -> new LoanView(loan.getId(), loan.getCopy().getTitle().getTitle(),
                loan.getCopy().getBarcode(), loan.getIssuedAt(), loan.getDueAt(), loan.isRenewed(), loan.overdue(now))).toList();
        List<LibraryHold> mineHeld = holds.findActiveForAccount(accountId);
        Map<Long, List<LibraryHold>> queue = mineHeld.isEmpty() ? Map.of()
                : holds.findActiveForTitles(mineHeld.stream().map(h -> h.getTitle().getId()).toList()).stream().collect(Collectors.groupingBy(h -> h.getTitle().getId()));
        List<HoldView> held = mineHeld.stream().map(hold -> new HoldView(hold.getId(), hold.getTitle().getId(), hold.getTitle().getTitle(),
                queue.getOrDefault(hold.getTitle().getId(), List.of()).stream().map(LibraryHold::getId).toList().indexOf(hold.getId()) + 1)).toList();
        return new MyLibrary(open, held);
    }

    public void placeHold(Long accountId, Long titleId) {
        LibraryTitle title = (titleId == null ? Optional.<LibraryTitle>empty() : titles.findByIdForUpdate(titleId)).filter(LibraryTitle::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        boolean borrowing = loans.findOpenForTitleForShare(title.getId()).stream().anyMatch(l -> l.getBorrower().getId().equals(accountId));
        if (borrowing) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have a copy of this title.");
        }
        try {
            holds.saveAndFlush(new LibraryHold(title, accounts.getReferenceById(accountId), now()));
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, "You are already in the queue for this title.");
        }
    }

    public void cancelHold(Long accountId, Long holdId) {
        LibraryHold hold = (holdId == null ? Optional.<LibraryHold>empty() : holds.findByIdForUpdate(holdId))
                .filter(found -> found.getAccount().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        valid(() -> {
            hold.close(LibraryHold.Status.CANCELLED, now());
            return hold;
        });
    }

    /** Renews one's own loan once, before it is due, unless others are waiting for the title. */
    public void renew(Long accountId, Long loanId) {
        LibraryLoan loan = (loanId == null ? Optional.<LibraryLoan>empty() : loans.findByIdForUpdate(loanId))
                .filter(found -> found.getBorrower().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (!holds.findActiveForTitleForUpdate(loan.getCopy().getTitle().getId()).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Others are waiting for this title, so it cannot be renewed.");
        }
        valid(() -> {
            loan.renew(now());
            return loan;
        });
    }

    @Transactional(readOnly = true)
    public List<ResourceView> resources(boolean includeInactive) {
        return (includeInactive ? resources.findAllByOrderByTitleAsc() : resources.findByActiveTrueOrderByTitleAsc()).stream()
                .map(r -> new ResourceView(r.getId(), r.getTitle(), r.getDescription(), r.getUrl(), r.isActive())).toList();
    }

    // --- Librarians

    public TitleView addTitle(String title, String authors, String isbn, String publisher, Integer year, String subject) {
        LibraryTitle saved;
        try {
            saved = titles.saveAndFlush(valid(() -> new LibraryTitle(title, authors, isbn, publisher, year, subject, now())));
        } catch (DataIntegrityViolationException taken) {
            throw new ApiException(HttpStatus.CONFLICT, "A title with this ISBN is already in the catalog.");
        }
        return new TitleView(saved.getId(), saved.getTitle(), saved.getAuthors(), saved.getIsbn(), saved.getPublisher(), saved.getPublishedYear(),
                saved.getSubject(), true, 0, 0, 0, null);
    }

    public void setTitleActive(Long titleId, boolean active) {
        LibraryTitle title = titles.findByIdForUpdate(titleId == null ? -1L : titleId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        title.edit(title.getTitle(), title.getAuthors(), title.getIsbn(), title.getPublisher(), title.getPublishedYear(), title.getSubject(), active);
    }

    @Transactional(readOnly = true)
    public List<CopyView> copiesOf(Long titleId) {
        List<LibraryCopy> its = copies.findForTitles(List.of(titleId == null ? -1L : titleId));
        Map<Long, LibraryLoan> open = its.isEmpty() ? Map.of()
                : loans.findOpenForCopies(its.stream().map(LibraryCopy::getId).toList()).stream().collect(Collectors.toMap(l -> l.getCopy().getId(), l -> l));
        return its.stream().map(copy -> {
            LibraryLoan loan = open.get(copy.getId());
            return new CopyView(copy.getId(), copy.getBarcode(), copy.getShelf(), copy.getWithdrawnAt() != null,
                    loan == null ? null : loan.getBorrower().getLoginId(), loan == null ? null : loan.getDueAt());
        }).toList();
    }

    public void addCopy(Long titleId, String barcode, String shelf) {
        LibraryTitle title = titles.findByIdForUpdate(titleId == null ? -1L : titleId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        try {
            copies.saveAndFlush(valid(() -> new LibraryCopy(title, barcode, shelf, now())));
        } catch (DataIntegrityViolationException taken) {
            throw new ApiException(HttpStatus.CONFLICT, "A copy with this barcode already exists.");
        }
    }

    public void withdrawCopy(String barcode) {
        LibraryCopy copy = copies.findByBarcodeForUpdate(LibraryCopy.normalize(barcode)).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (loans.findOpenForCopyForUpdate(copy.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "This copy is on loan; take it back first.");
        }
        copy.withdraw(now());
    }

    /** Lends a copy to an active student or faculty account, within the loan limit and the title's hold queue. */
    public LoanView issue(Long librarianId, String barcode, String loginId) {
        Long borrowerId = (loginId == null ? Optional.<UserAccount>empty() : accounts.findWithRolesAndPermissionsByLoginId(loginId.strip()))
                .filter(found -> found.getStatus() == AccountStatus.ACTIVE && (found.hasRole(RoleName.STUDENT) || found.hasRole(RoleName.FACULTY)))
                .map(UserAccount::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Lend only to active student or faculty accounts."));
        UserAccount borrower = accounts.findByIdForUpdate(borrowerId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        LibraryCopy copy = copies.findByBarcodeForUpdate(LibraryCopy.normalize(barcode))
                .filter(found -> found.getWithdrawnAt() == null && found.getTitle().isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No copy in service with that barcode."));
        if (loans.findOpenForCopyForUpdate(copy.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "This copy is already on loan.");
        }
        if (loans.findOpenForBorrowerForUpdate(borrowerId).size() >= LOAN_LIMIT) {
            throw new ApiException(HttpStatus.CONFLICT, "This borrower already has " + LOAN_LIMIT + " books out.");
        }
        Long titleId = copy.getTitle().getId();
        // Copies and loans before holds: the same order as renewing (loan, then holds), so the two never deadlock.
        int available = copies.findInServiceForShare(titleId).size() - loans.findOpenForTitleForShare(titleId).size();
        List<LibraryHold> queue = holds.findActiveForTitleForUpdate(titleId);
        Optional<LibraryHold> own = queue.stream().filter(hold -> hold.getAccount().getId().equals(borrowerId)).findFirst();
        int ahead = own.map(queue::indexOf).orElse(queue.size());
        if (ahead >= available) {
            throw new ApiException(HttpStatus.CONFLICT, "The available copies are held for others in the queue.");
        }
        own.ifPresent(hold -> hold.close(LibraryHold.Status.FULFILLED, now()));
        LibraryLoan saved;
        try {
            saved = loans.saveAndFlush(new LibraryLoan(copy, borrower, librarianId, now()));
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, "This copy is already on loan.");
        }
        return new LoanView(saved.getId(), copy.getTitle().getTitle(), copy.getBarcode(), saved.getIssuedAt(), saved.getDueAt(), false, false);
    }

    /** Takes a copy back; taking it back again changes nothing. */
    public String giveBack(String barcode) {
        LibraryCopy copy = copies.findByBarcodeForUpdate(LibraryCopy.normalize(barcode)).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        return loans.findOpenForCopyForUpdate(copy.getId()).map(loan -> {
            loan.giveBack(now());
            return loan.getBorrower().getLoginId();
        }).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "This copy is not on loan."));
    }

    @Transactional(readOnly = true)
    public List<DeskLoan> openLoans() {
        Instant now = now();
        return loans.findAllOpen().stream().map(loan -> new DeskLoan(loan.getId(), loan.getBorrower().getLoginId(), loan.getCopy().getTitle().getTitle(),
                loan.getCopy().getBarcode(), loan.getDueAt(), loan.overdue(now))).toList();
    }

    public ResourceView addResource(String title, String description, String url) {
        LibraryResource saved = resources.saveAndFlush(valid(() -> new LibraryResource(title, description, url, now())));
        return new ResourceView(saved.getId(), saved.getTitle(), saved.getDescription(), saved.getUrl(), true);
    }

    public void setResourceActive(Long resourceId, boolean active) {
        resources.findById(resourceId == null ? -1L : resourceId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND)).setActive(active);
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
