package com.stacc.backend.innovation;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Innovation (v0.12.11). Submitters see their own ideas and, once decided, the reviewers' comments without names.
 * Reviewers see open ideas other than their own with only their own review; the campus office sees every idea with
 * every review and decides. Every change locks the idea row first.
 */
@Service
@Transactional
public class InnovationService {

    static final String NOT_FOUND = "Idea not found.";
    private static final Set<InnovationIdea.Status> OPEN = Set.of(InnovationIdea.Status.SUBMITTED, InnovationIdea.Status.SHORTLISTED);

    public record ReviewView(String reviewerLoginId, int score, String comment, Instant reviewedAt) {
    }

    public record IdeaView(Long ideaId, String title, String problem, String solution, InnovationIdea.Status status, String submitterLoginId,
            Instant submittedAt, String decisionNote, Instant decidedAt, List<ReviewView> reviews) {
    }

    private final InnovationIdeaRepository ideas;
    private final InnovationReviewRepository reviews;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public InnovationService(InnovationIdeaRepository ideas, InnovationReviewRepository reviews, UserAccountRepository accounts, Clock clock) {
        this.ideas = ideas;
        this.reviews = reviews;
        this.accounts = accounts;
        this.clock = clock;
    }

    // --- Submitters

    @Transactional(readOnly = true)
    public List<IdeaView> mine(Long accountId) {
        List<InnovationIdea> found = ideas.findForSubmitter(accountId);
        Map<Long, List<InnovationReview>> byIdea = reviewsOf(found);
        // Comments reach the submitter only once the idea is decided, and never with the reviewer's name or score.
        return found.stream().map(idea -> view(idea, null, idea.decided() ? byIdea.getOrDefault(idea.getId(), List.of()).stream()
                .map(review -> new ReviewView(null, 0, review.getComment(), review.getReviewedAt())).toList() : List.of())).toList();
    }

    public IdeaView submit(Long accountId, String title, String problem, String solution) {
        InnovationIdea saved = ideas.saveAndFlush(valid(() -> new InnovationIdea(accounts.getReferenceById(accountId), title, problem, solution, now())));
        return view(saved, null, List.of());
    }

    public IdeaView withdraw(Long accountId, Long ideaId) {
        InnovationIdea idea = locked(ideaId);
        if (!idea.getSubmitter().getId().equals(accountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        valid(() -> {
            idea.withdraw();
            return idea;
        });
        return view(idea, null, List.of());
    }

    // --- Reviewers

    @Transactional(readOnly = true)
    public List<IdeaView> forReview(Long accountId) {
        List<InnovationIdea> found = ideas.findOpenForReviewer(OPEN, accountId);
        Map<Long, List<InnovationReview>> byIdea = reviewsOf(found);
        return found.stream().map(idea -> view(idea, null, byIdea.getOrDefault(idea.getId(), List.of()).stream()
                .filter(review -> review.getReviewer().getId().equals(accountId)).map(InnovationService::reviewView).toList())).toList();
    }

    /** Gives or replaces the reviewer's score and comment on an open idea that is not their own. */
    public void review(Long accountId, Long ideaId, int score, String comment) {
        InnovationIdea idea = locked(ideaId);
        if (idea.getSubmitter().getId().equals(accountId) || !idea.open()) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        InnovationReview review = reviews.findOne(ideaId, accountId).orElseGet(() -> new InnovationReview(idea, accounts.getReferenceById(accountId)));
        valid(() -> {
            review.set(score, comment, now());
            return review;
        });
        try {
            reviews.saveAndFlush(review);
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, "Your review was saved meanwhile; reload and try again.");
        }
    }

    // --- The campus office

    @Transactional(readOnly = true)
    public List<IdeaView> all() {
        List<InnovationIdea> found = ideas.findAllForOffice();
        Map<Long, List<InnovationReview>> byIdea = reviewsOf(found);
        return found.stream().map(idea -> view(idea, idea.getSubmitter().getLoginId(),
                byIdea.getOrDefault(idea.getId(), List.of()).stream().map(InnovationService::reviewView).toList())).toList();
    }

    public void decide(Long ideaId, InnovationIdea.Status status, String note) {
        InnovationIdea idea = locked(ideaId);
        valid(() -> {
            idea.decide(status, note, now());
            return idea;
        });
        ideas.flush();
    }

    private InnovationIdea locked(Long ideaId) {
        return (ideaId == null ? Optional.<InnovationIdea>empty() : ideas.findByIdForUpdate(ideaId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private Map<Long, List<InnovationReview>> reviewsOf(List<InnovationIdea> found) {
        return found.isEmpty() ? Map.of() : reviews.findForIdeas(found.stream().map(InnovationIdea::getId).toList()).stream()
                .collect(Collectors.groupingBy(review -> review.getIdea().getId()));
    }

    private static ReviewView reviewView(InnovationReview review) {
        return new ReviewView(review.getReviewer().getLoginId(), review.getScore(), review.getComment(), review.getReviewedAt());
    }

    private static IdeaView view(InnovationIdea idea, String submitterLoginId, List<ReviewView> shown) {
        return new IdeaView(idea.getId(), idea.getTitle(), idea.getProblem(), idea.getSolution(), idea.getStatus(), submitterLoginId,
                idea.getSubmittedAt(), idea.decided() || idea.getStatus() == InnovationIdea.Status.SHORTLISTED ? idea.getDecisionNote() : null,
                idea.getDecidedAt(), shown);
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
