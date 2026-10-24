package com.stacc.backend.lms.assignment.submission;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.faculty.FacultyProfileStatus;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentNotifications;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faculty grade one attempt at a time. The attempt's course is found from the stored attempt, never from the
 * request, and {@link LmsFacultyAccessService} must allow the signed-in account to use it; the grader is that
 * account's own faculty profile. An attempt that does not exist, one of an assignment that is not published,
 * and one in a course the caller does not teach all get the same 404.
 *
 * <p>The first grade keeps the assignment's maximum marks at that moment; a revision keeps that maximum and
 * replaces only the marks and feedback, recording the latest grader and time. Lateness never changes the
 * marks: faculty decide them.
 */
@Service
@Transactional
public class LmsAssignmentGradingService {

    static final String ATTEMPT_UNAVAILABLE = "Attempt not found or unavailable.";
    static final String NEGATIVE_MARKS = "Marks cannot be negative.";
    static final String FEEDBACK_TOO_LONG = "Feedback must be at most 10,000 characters.";

    private final LmsSubmissionAttemptRepository attempts;
    private final LmsSubmissionGradeRepository grades;
    private final LmsFacultyAccessService facultyAccess;
    private final FacultyProfileRepository facultyProfiles;
    private final Clock clock;
    private final LmsAssignmentNotifications notifications;

    public LmsAssignmentGradingService(
            LmsSubmissionAttemptRepository attempts,
            LmsSubmissionGradeRepository grades,
            LmsFacultyAccessService facultyAccess,
            FacultyProfileRepository facultyProfiles,
            Clock clock,
            LmsAssignmentNotifications notifications) {
        this.attempts = attempts;
        this.grades = grades;
        this.facultyAccess = facultyAccess;
        this.facultyProfiles = facultyProfiles;
        this.clock = clock;
        this.notifications = notifications;
    }

    /**
     * Grades the attempt, or revises its grade. The marks must be from zero up to the maximum: the
     * assignment's maximum now for a first grade, the grade's own maximum for a revision.
     *
     * <p>The attempt row is locked first and the grade is read with a lock, so two graders of the same attempt
     * take turns and its first grade is created once; a unique constraint backs this up.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public LmsSubmissionGrade grade(Long userAccountId, Long attemptId, BigDecimal marksAwarded, String feedback) {
        LmsSubmissionAttempt attempt = attemptId == null ? null : attempts.findWithAssignmentById(attemptId).orElse(null);
        if (attempt == null || !isGradable(userAccountId, attempt)) {
            throw notFound();
        }
        // The same active profile the access rule just accepted for this account.
        FacultyProfile grader = facultyProfiles.findByUserAccountId(userAccountId)
                .filter(profile -> profile.getStatus() == FacultyProfileStatus.ACTIVE)
                .orElseThrow(LmsAssignmentGradingService::notFound);
        if (marksAwarded == null || marksAwarded.signum() < 0) {
            throw badRequest(NEGATIVE_MARKS);
        }
        String validFeedback = validFeedback(feedback);

        attempts.findByIdForUpdate(attempt.getId()).orElseThrow(LmsAssignmentGradingService::notFound);
        // The database keeps microseconds, so the answer shows the same moment as every later read.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Optional<LmsSubmissionGrade> existing = grades.findForUpdateByAttemptId(attempt.getId());
        if (existing.isPresent()) {
            LmsSubmissionGrade grade = existing.get();
            requireWithin(marksAwarded, grade.getMaxMarksSnapshot());
            // The student hears about a revision only when the marks or the feedback really changed.
            boolean changed = grade.getMarksAwarded().compareTo(marksAwarded) != 0
                    || !Objects.equals(grade.getFeedback(), validFeedback);
            grade.revise(grader, marksAwarded, validFeedback, now);
            LmsSubmissionGrade saved = grades.saveAndFlush(grade);
            if (changed) {
                notifications.graded(saved, false);
            }
            return saved;
        }
        BigDecimal maxMarks = attempt.getSubmission().getAssignment().getMaxMarks();
        requireWithin(marksAwarded, maxMarks);
        LmsSubmissionGrade saved = grades.saveAndFlush(
                new LmsSubmissionGrade(attempt, grader, marksAwarded, maxMarks, validFeedback, now));
        notifications.graded(saved, true);
        return saved;
    }

    // Grading is part of the normal workflow of a published assignment in a course the caller teaches now.
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
