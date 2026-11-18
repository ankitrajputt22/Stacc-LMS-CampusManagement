package com.stacc.backend.lms.assignment.submission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.faculty.FacultyProfileStatus;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentNotifications;
import com.stacc.backend.lms.assignment.rubric.LmsRubric;
import com.stacc.backend.lms.assignment.rubric.LmsRubricCriterion;
import com.stacc.backend.lms.assignment.rubric.LmsRubricRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LmsAssignmentGradingService {

    static final String ATTEMPT_UNAVAILABLE = "Attempt not found or unavailable.";
    static final String NEGATIVE_MARKS = "Marks cannot be negative.";
    static final String FEEDBACK_TOO_LONG = "Feedback must be at most 10,000 characters.";
    static final String NO_RUBRIC = "This assignment is not graded with a rubric.";
    static final String SCORES_INVALID = "Give points for every criterion of the rubric exactly once.";

    private final LmsSubmissionAttemptRepository attempts;
    private final LmsSubmissionGradeRepository grades;
    private final LmsFacultyAccessService facultyAccess;
    private final FacultyProfileRepository facultyProfiles;
    private final Clock clock;
    private final LmsAssignmentNotifications notifications;
    private final LmsRubricRepository rubrics;

    public LmsAssignmentGradingService(
            LmsSubmissionAttemptRepository attempts,
            LmsSubmissionGradeRepository grades,
            LmsFacultyAccessService facultyAccess,
            FacultyProfileRepository facultyProfiles,
            Clock clock,
            LmsAssignmentNotifications notifications,
            LmsRubricRepository rubrics) {
        this.attempts = attempts;
        this.grades = grades;
        this.facultyAccess = facultyAccess;
        this.facultyProfiles = facultyProfiles;
        this.clock = clock;
        this.notifications = notifications;
        this.rubrics = rubrics;
    }

    public LmsSubmissionGrade grade(Long userAccountId, Long attemptId, BigDecimal marksAwarded, String feedback) {
        LmsSubmissionAttempt attempt = gradable(userAccountId, attemptId);
        FacultyProfile grader = grader(userAccountId);
        if (marksAwarded == null || marksAwarded.signum() < 0) {
            throw badRequest(NEGATIVE_MARKS);
        }
        String validFeedback = validFeedback(feedback);
        attempts.findByIdForUpdate(attempt.getId()).orElseThrow(LmsAssignmentGradingService::notFound);
        return save(attempt, grader, marksAwarded, validFeedback, LmsSubmissionGrade::clearRubric);
    }

    public record RubricScoreInput(Long criterionId, BigDecimal points, String comment) {
    }

    public LmsSubmissionGrade gradeWithRubric(Long userAccountId, Long attemptId, List<RubricScoreInput> scores, String feedback) {
        LmsSubmissionAttempt attempt = gradable(userAccountId, attemptId);
        FacultyProfile grader = grader(userAccountId);
        String validFeedback = validFeedback(feedback);
        LmsRubric chosen = attempt.getSubmission().getAssignment().getRubric();
        if (chosen == null) {
            throw new ApiException(HttpStatus.CONFLICT, NO_RUBRIC);
        }
        attempts.findByIdForUpdate(attempt.getId()).orElseThrow(LmsAssignmentGradingService::notFound);
        rubrics.lockRowForShare(chosen.getId());
        LmsRubric rubric = rubrics.findByIdForShare(chosen.getId()).orElseThrow(LmsAssignmentGradingService::notFound);
        java.util.Set<Long> stored = new java.util.HashSet<>(rubrics.findCurrentCriterionIdsForShare(rubric.getId()));
        if (!stored.equals(rubric.getCriteria().stream().map(LmsRubricCriterion::getId).collect(java.util.stream.Collectors.toSet()))) {
            throw new ApiException(HttpStatus.CONFLICT, "The rubric changed while you were grading. Reload the page and grade again.");
        }
        Map<Long, RubricScoreInput> byCriterion = new HashMap<>();
        for (RubricScoreInput score : scores == null ? List.<RubricScoreInput>of() : scores) {
            if (score == null || score.criterionId() == null || byCriterion.put(score.criterionId(), score) != null) {
                throw badRequest(SCORES_INVALID);
            }
        }
        List<LmsRubricCriterion> criteria = rubric.getCriteria();
        if (byCriterion.size() != criteria.size()
                || !criteria.stream().map(LmsRubricCriterion::getId).allMatch(byCriterion::containsKey)) {
            throw badRequest(SCORES_INVALID);
        }
        List<LmsRubricScore> made = new ArrayList<>();
        BigDecimal marks = BigDecimal.ZERO.setScale(2);
        for (LmsRubricCriterion criterion : criteria) {
            RubricScoreInput score = byCriterion.get(criterion.getId());
            BigDecimal points = score.points();
            if (points == null || points.signum() < 0 || points.compareTo(criterion.getMaxPoints()) > 0
                    || points.stripTrailingZeros().scale() > 2) {
                throw badRequest(criterion.getTitle() + ": points must be from 0 to "
                        + criterion.getMaxPoints().stripTrailingZeros().toPlainString() + ", with at most 2 decimal places.");
            }
            String comment = score.comment() == null || score.comment().isBlank() ? null : score.comment().strip();
            if (comment != null && comment.length() > LmsRubricScore.MAX_COMMENT_LENGTH) {
                throw badRequest(criterion.getTitle() + ": the comment must be at most 2,000 characters.");
            }
            BigDecimal kept = points.setScale(2, RoundingMode.UNNECESSARY);
            made.add(new LmsRubricScore(criterion.getPosition(), criterion.getId(), criterion.getTitle(), criterion.getMaxPoints(),
                    kept, comment));
            marks = marks.add(kept);
        }
        return save(attempt, grader, marks, validFeedback, grade -> grade.useRubric(made));
    }

    private LmsSubmissionGrade save(LmsSubmissionAttempt attempt, FacultyProfile grader, BigDecimal marksAwarded,
            String validFeedback, Consumer<LmsSubmissionGrade> rubricScores) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Optional<LmsSubmissionGrade> existing = grades.findForUpdateByAttemptId(attempt.getId());
        if (existing.isPresent()) {
            LmsSubmissionGrade grade = existing.get();
            requireWithin(marksAwarded, grade.getMaxMarksSnapshot());
            boolean changed = grade.getMarksAwarded().compareTo(marksAwarded) != 0
                    || !Objects.equals(grade.getFeedback(), validFeedback);
            grade.revise(grader, marksAwarded, validFeedback, now);
            rubricScores.accept(grade);
            LmsSubmissionGrade saved = grades.saveAndFlush(grade);
            if (changed) {
                notifications.graded(saved, false);
            }
            return saved;
        }
        BigDecimal maxMarks = attempt.getSubmission().getAssignment().getMaxMarks();
        requireWithin(marksAwarded, maxMarks);
        LmsSubmissionGrade grade = new LmsSubmissionGrade(attempt, grader, marksAwarded, maxMarks, validFeedback, now);
        rubricScores.accept(grade);
        LmsSubmissionGrade saved = grades.saveAndFlush(grade);
        notifications.graded(saved, true);
        return saved;
    }

    private LmsSubmissionAttempt gradable(Long userAccountId, Long attemptId) {
        LmsSubmissionAttempt attempt = attemptId == null ? null : attempts.findWithAssignmentById(attemptId).orElse(null);
        if (attempt == null || !isGradable(userAccountId, attempt)) {
            throw notFound();
        }
        return attempt;
    }

    private FacultyProfile grader(Long userAccountId) {
        return facultyProfiles.findByUserAccountId(userAccountId)
                .filter(profile -> profile.getStatus() == FacultyProfileStatus.ACTIVE)
                .orElseThrow(LmsAssignmentGradingService::notFound);
    }

    private boolean isGradable(Long userAccountId, LmsSubmissionAttempt attempt) {
        LmsAssignment assignment = attempt.getSubmission().getAssignment();
        return assignment.getStatus() == LmsContentStatus.PUBLISHED
                && facultyAccess.canAccessCourse(userAccountId, assignment.getLmsCourse().getId());
    }

    private static void requireWithin(BigDecimal marksAwarded, BigDecimal maxMarks) {
        if (marksAwarded.compareTo(maxMarks) > 0) {
            throw badRequest("Marks cannot exceed " + maxMarks.stripTrailingZeros().toPlainString() + ".");
        }
    }

    private static String validFeedback(String feedback) {
        if (feedback != null && feedback.length() > LmsSubmissionGrade.MAX_FEEDBACK_LENGTH) {
            throw badRequest(FEEDBACK_TOO_LONG);
        }
        return LmsSubmissionGrade.feedbackOrNull(feedback);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ATTEMPT_UNAVAILABLE);
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
