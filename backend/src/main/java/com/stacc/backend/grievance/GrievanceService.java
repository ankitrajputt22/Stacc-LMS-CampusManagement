package com.stacc.backend.grievance;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grievances (v0.12.3) for complainants and committees. A case is confidential: its complainant and the active
 * committee members of its category (never the complainant themselves) open it, and anyone else gets the same 404 as
 * for a missing case. There is no admin pass. Every change locks the case row; overdue is worked out on read.
 */
@Service
@Transactional
public class GrievanceService {

    static final String NOT_FOUND = "Grievance not found.";
    static final String CATEGORY_NOT_FOUND = "Choose an open category.";

    public record CategoryOption(Long categoryId, String name, int responseDays) {
    }

    public record CaseSummary(Long caseId, String caseNumber, String categoryName, String subject, GrievanceCase.Status status,
            Instant submittedAt, Instant respondBy, boolean overdue) {
    }

    /** One case; the complainant's login ID is sent to the committee only. */
    public record CaseView(Long caseId, String caseNumber, String categoryName, String subject, String details, GrievanceCase.Status status,
            Instant submittedAt, Instant respondBy, boolean overdue, Instant reviewStartedAt, String resolution, Instant resolvedAt,
            String appealReason, Instant appealedAt, Instant appealRespondBy, String appealDecision, Instant closedAt,
            Instant appealUntil, String complainantLoginId) {
    }

    private final GrievanceCaseRepository cases;
    private final GrievanceCategoryRepository categories;
    private final GrievanceCommitteeMemberRepository committees;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public GrievanceService(GrievanceCaseRepository cases, GrievanceCategoryRepository categories, GrievanceCommitteeMemberRepository committees,
            UserAccountRepository accounts, Clock clock) {
        this.cases = cases;
        this.categories = categories;
        this.committees = committees;
        this.accounts = accounts;
        this.clock = clock;
    }

    // --- Complainants

    @Transactional(readOnly = true)
    public List<CategoryOption> openCategories() {
        return categories.findByActiveTrueOrderByNameAsc().stream()
                .map(category -> new CategoryOption(category.getId(), category.getName(), category.getResponseDays())).toList();
    }

    public CaseView submit(Long accountId, Long categoryId, String subject, String details) {
        GrievanceCategory category = (categoryId == null ? Optional.<GrievanceCategory>empty() : categories.findById(categoryId))
                .filter(GrievanceCategory::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, CATEGORY_NOT_FOUND));
        GrievanceCase saved = cases.saveAndFlush(valid(() -> new GrievanceCase(category, accounts.getReferenceById(accountId), subject, details, now())));
        saved.number();
        cases.flush();
        return view(saved, false);
    }

    @Transactional(readOnly = true)
    public List<CaseSummary> mine(Long accountId) {
        return cases.findForComplainant(accountId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public CaseView myCase(Long accountId, Long caseId) {
        return view(own(accountId, caseId, false), false);
    }

    public CaseView appeal(Long accountId, Long caseId, String reason) {
        GrievanceCase found = own(accountId, caseId, true);
        valid(() -> {
            found.appeal(reason, now());
            return found;
        });
        return view(found, false);
    }

    public CaseView accept(Long accountId, Long caseId) {
        GrievanceCase found = own(accountId, caseId, true);
        valid(() -> {
            found.accept(now());
            return found;
        });
        return view(found, false);
    }

    // --- Committees

    @Transactional(readOnly = true)
    public List<CaseSummary> committeeCases(Long accountId) {
        List<Long> categoryIds = committees.findCategoryIdsFor(accountId);
        return categoryIds.isEmpty() ? List.of() : cases.findForCommittee(categoryIds, accountId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public CaseView committeeCase(Long accountId, Long caseId) {
        return view(committeeOnly(accountId, caseId, false), true);
    }

    public CaseView startReview(Long accountId, Long caseId) {
        GrievanceCase found = committeeOnly(accountId, caseId, true);
        valid(() -> found.startReview(now()));
        return view(found, true);
    }

    public CaseView resolve(Long accountId, Long caseId, String resolution) {
        GrievanceCase found = committeeOnly(accountId, caseId, true);
        valid(() -> {
            found.resolve(resolution, now());
            return found;
        });
        return view(found, true);
    }

    public CaseView decideAppeal(Long accountId, Long caseId, String decision) {
        GrievanceCase found = committeeOnly(accountId, caseId, true);
        valid(() -> {
            found.decideAppeal(decision, now());
            return found;
        });
        return view(found, true);
    }

    private GrievanceCase own(Long accountId, Long caseId, boolean lock) {
        return load(caseId, lock).filter(found -> found.getComplainant().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    /** A committee member of the case's category who is not its complainant; anyone else gets the same 404. */
    private GrievanceCase committeeOnly(Long accountId, Long caseId, boolean lock) {
        List<Long> categoryIds = committees.findCategoryIdsFor(accountId);
        return load(caseId, lock)
                .filter(found -> categoryIds.contains(found.getCategory().getId()))
                .filter(found -> !found.getComplainant().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private Optional<GrievanceCase> load(Long caseId, boolean lock) {
        if (caseId == null) {
            return Optional.empty();
        }
        return lock ? cases.findByIdForUpdate(caseId) : cases.findById(caseId);
    }

    private CaseSummary summary(GrievanceCase found) {
        return new CaseSummary(found.getId(), found.getCaseNumber(), found.getCategory().getName(), found.getSubject(), found.getStatus(),
                found.getSubmittedAt(), found.getStatus() == GrievanceCase.Status.APPEALED ? found.getAppealRespondBy() : found.getRespondBy(),
                found.overdue(now()));
    }

    private CaseView view(GrievanceCase found, boolean committee) {
        Instant appealUntil = found.getStatus() == GrievanceCase.Status.RESOLVED ? found.getResolvedAt().plus(GrievanceCase.APPEAL_WINDOW) : null;
        return new CaseView(found.getId(), found.getCaseNumber(), found.getCategory().getName(), found.getSubject(), found.getDetails(),
                found.getStatus(), found.getSubmittedAt(), found.getRespondBy(), found.overdue(now()), found.getReviewStartedAt(),
                found.getResolution(), found.getResolvedAt(), found.getAppealReason(), found.getAppealedAt(), found.getAppealRespondBy(),
                found.getAppealDecision(), found.getClosedAt(), appealUntil, committee ? found.getComplainant().getLoginId() : null);
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
