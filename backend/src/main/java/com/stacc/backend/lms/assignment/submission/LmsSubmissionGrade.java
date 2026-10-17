package com.stacc.backend.lms.assignment.submission;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.assignment.LmsAssignment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Faculty's evaluation of one attempt: the marks awarded, out of the assignment's maximum at the time it
 * was first graded, with optional plain-text feedback. It belongs to the attempt, never to the student's
 * submission as a whole, so a new attempt starts ungraded and earlier grades stay with the work they were
 * given for. An attempt has at most one grade.
 *
 * <p>The grade can be revised: the marks and feedback change, and it records who saved it last and when.
 * The maximum it was graded out of never changes, even if the assignment's maximum does. Earlier values
 * are not kept.
 */
@Entity
@Table(name = "lms_submission_grades")
public class LmsSubmissionGrade {

    public static final int MAX_FEEDBACK_LENGTH = 10_000;

    // No cascade: the attempt and the faculty profile are never created or deleted through a grade.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true, updatable = false)
    private LmsSubmissionAttempt attempt;

    // Who saved the current marks and feedback: the latest grader, for the record.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "graded_by_faculty_profile_id", nullable = false)
    private FacultyProfile gradedByFacultyProfile;

    @Column(name = "marks_awarded", nullable = false, precision = 8, scale = LmsAssignment.MARK_DECIMAL_PLACES)
    private BigDecimal marksAwarded;

    // The assignment's maximum when the attempt was first graded. Changing the assignment never changes it.
    @Column(name = "max_marks_snapshot", nullable = false, updatable = false, precision = 8,
            scale = LmsAssignment.MARK_DECIMAL_PLACES)
    private BigDecimal maxMarksSnapshot;

    // Plain text, line breaks kept. It is never treated as HTML. Null when there is none.
    @Column(columnDefinition = "TEXT")
    private String feedback;

    // When the current marks and feedback were saved, by the server's clock.
    @Column(name = "graded_at", nullable = false)
    private Instant gradedAt;

    protected LmsSubmissionGrade() {
        // Required by JPA.
    }

    /**
     * The first grade of an attempt.
     *
     * @param maxMarksSnapshot the assignment's maximum marks now; it stays with this grade
     * @param gradedAt         the server's time, never a time from the client
     */
    public LmsSubmissionGrade(LmsSubmissionAttempt attempt, FacultyProfile gradedBy, BigDecimal marksAwarded,
            BigDecimal maxMarksSnapshot, String feedback, Instant gradedAt) {
        this.attempt = Require.notNull(attempt, "attempt");
        this.maxMarksSnapshot = validMarks(maxMarksSnapshot, "maxMarksSnapshot");
        if (this.maxMarksSnapshot.signum() <= 0) {
            throw new IllegalArgumentException("maxMarksSnapshot must be greater than zero");
        }
        record(gradedBy, marksAwarded, feedback, gradedAt);
    }

    /** Whether these marks fit this grade: from zero up to the maximum it was first graded out of. */
    public boolean accepts(BigDecimal marksAwarded) {
        return marksAwarded != null && marksAwarded.signum() >= 0 && marksAwarded.compareTo(maxMarksSnapshot) <= 0;
    }

    /**
     * Replaces the marks and feedback, for example to correct them. The maximum stays as it was, and the
     * grade now records this grader and time.
     */
    public void revise(FacultyProfile gradedBy, BigDecimal marksAwarded, String feedback, Instant gradedAt) {
        record(gradedBy, marksAwarded, feedback, gradedAt);
    }

    /** The feedback as it will be stored: unchanged, or null when it is missing or blank. Too long is refused. */
    public static String feedbackOrNull(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return null;
        }
        if (feedback.length() > MAX_FEEDBACK_LENGTH) {
            throw new IllegalArgumentException("feedback must be at most " + MAX_FEEDBACK_LENGTH + " characters");
        }
        return feedback;
    }

    public Long getId() {
        return id;
    }

    public LmsSubmissionAttempt getAttempt() {
        return attempt;
    }

    public FacultyProfile getGradedByFacultyProfile() {
        return gradedByFacultyProfile;
    }

    /** The marks awarded, always with two decimal places. */
    public BigDecimal getMarksAwarded() {
        return marksAwarded;
    }

    public BigDecimal getMaxMarksSnapshot() {
        return maxMarksSnapshot;
    }

    public String getFeedback() {
        return feedback;
    }

    public Instant getGradedAt() {
        return gradedAt;
    }

    private void record(FacultyProfile gradedBy, BigDecimal marksAwarded, String feedback, Instant gradedAt) {
        FacultyProfile validGrader = Require.notNull(gradedBy, "gradedBy");
        BigDecimal validMarks = validMarks(marksAwarded, "marksAwarded");
        if (!accepts(validMarks)) {
            throw new IllegalArgumentException("marksAwarded must be between 0 and " + maxMarksSnapshot);
        }
        String validFeedback = feedbackOrNull(feedback);
        Instant validTime = Require.notNull(gradedAt, "gradedAt");
        this.gradedByFacultyProfile = validGrader;
        this.marksAwarded = validMarks;
        this.feedback = validFeedback;
        this.gradedAt = validTime;
    }

    // Marks may be fractional, such as 12.5. More decimal places than can be stored are refused, never rounded.
    private static BigDecimal validMarks(BigDecimal marks, String name) {
        Require.notNull(marks, name);
        if (marks.stripTrailingZeros().scale() > LmsAssignment.MARK_DECIMAL_PLACES) {
            throw new IllegalArgumentException(name + " must have at most " + LmsAssignment.MARK_DECIMAL_PLACES + " decimal places");
        }
        if (marks.compareTo(LmsAssignment.MAX_MARKS) > 0) {
            throw new IllegalArgumentException(name + " must be at most " + LmsAssignment.MAX_MARKS);
        }
        return marks.setScale(LmsAssignment.MARK_DECIMAL_PLACES);
    }
}
