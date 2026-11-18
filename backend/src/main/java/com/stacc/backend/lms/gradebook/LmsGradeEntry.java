package com.stacc.backend.lms.gradebook;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "lms_grade_entries")
public class LmsGradeEntry {

    public static final int MAX_FEEDBACK_LENGTH = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_grade_item_id", nullable = false, updatable = false)
    private LmsGradeItem item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_enrollment_id", nullable = false, updatable = false)
    private CourseEnrollment courseEnrollment;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal marks;

    @Column(length = MAX_FEEDBACK_LENGTH)
    private String feedback;

    @Column(name = "graded_by_account_id", nullable = false)
    private Long gradedByAccountId;

    @Column(name = "graded_at", nullable = false)
    private Instant gradedAt;

    protected LmsGradeEntry() {
    }

    public LmsGradeEntry(LmsGradeItem item, CourseEnrollment courseEnrollment) {
        this.item = Require.notNull(item, "item");
        this.courseEnrollment = Require.notNull(courseEnrollment, "courseEnrollment");
    }

    public void grade(BigDecimal marks, String feedback, Long gradedByAccountId, Instant at) {
        Require.notNull(marks, "Marks");
        BigDecimal maximum = item.getMaxMarks();
        if (marks.signum() < 0 || marks.compareTo(maximum) > 0 || marks.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Marks must be from 0 to " + maximum.stripTrailingZeros().toPlainString()
                    + ", with at most 2 decimal places.");
        }
        this.marks = marks.setScale(2, RoundingMode.UNNECESSARY);
        this.feedback = Require.optionalText(feedback, "Feedback", MAX_FEEDBACK_LENGTH);
        this.gradedByAccountId = Require.notNull(gradedByAccountId, "gradedByAccountId");
        this.gradedAt = Require.notNull(at, "at");
    }

    public Long getId() {
        return id;
    }

    public LmsGradeItem getItem() {
        return item;
    }

    public CourseEnrollment getCourseEnrollment() {
        return courseEnrollment;
    }

    public BigDecimal getMarks() {
        return marks;
    }

    public String getFeedback() {
        return feedback;
    }

    public Instant getGradedAt() {
        return gradedAt;
    }
}
