package com.stacc.backend.lms.assignment.submission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.assignment.LmsAssignment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "lms_submission_grades")
public class LmsSubmissionGrade {

    public static final int MAX_FEEDBACK_LENGTH = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true, updatable = false)
    private LmsSubmissionAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "graded_by_faculty_profile_id", nullable = false)
    private FacultyProfile gradedByFacultyProfile;

    @Column(name = "marks_awarded", nullable = false, precision = 8, scale = LmsAssignment.MARK_DECIMAL_PLACES)
    private BigDecimal marksAwarded;

    @Column(name = "max_marks_snapshot", nullable = false, updatable = false, precision = 8,
            scale = LmsAssignment.MARK_DECIMAL_PLACES)
    private BigDecimal maxMarksSnapshot;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "graded_at", nullable = false)
    private Instant gradedAt;

    @OneToOne(mappedBy = "grade", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private LmsRubricAssessment rubricAssessment;

    protected LmsSubmissionGrade() {
    }

    public LmsSubmissionGrade(LmsSubmissionAttempt attempt, FacultyProfile gradedBy, BigDecimal marksAwarded,
            BigDecimal maxMarksSnapshot, String feedback, Instant gradedAt) {
        this.attempt = Require.notNull(attempt, "attempt");
        this.maxMarksSnapshot = validMarks(maxMarksSnapshot, "maxMarksSnapshot");
        if (this.maxMarksSnapshot.signum() <= 0) {
            throw new IllegalArgumentException("maxMarksSnapshot must be greater than zero");
        }
        record(gradedBy, marksAwarded, feedback, gradedAt);
    }

    public boolean accepts(BigDecimal marksAwarded) {
        return marksAwarded != null && marksAwarded.signum() >= 0 && marksAwarded.compareTo(maxMarksSnapshot) <= 0;
    }

    public void revise(FacultyProfile gradedBy, BigDecimal marksAwarded, String feedback, Instant gradedAt) {
        record(gradedBy, marksAwarded, feedback, gradedAt);
    }

    public void useRubric(List<LmsRubricScore> scores) {
        if (rubricAssessment == null) {
            rubricAssessment = new LmsRubricAssessment(this);
        }
        rubricAssessment.replace(scores);
    }

    public void clearRubric() {
        this.rubricAssessment = null;
    }

    public List<LmsRubricScore> getRubricScores() {
        return rubricAssessment == null ? List.of() : rubricAssessment.getScores();
    }

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
