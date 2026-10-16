package com.stacc.backend.lms.assignment.submission;

import java.time.Instant;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.common.validation.Require;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One student's submission record for one assignment: the parent of every attempt they make. It belongs to
 * the student's official course enrollment, not to an account or profile, so the ERP stays the source of
 * truth for who the student is and which course they take. There is at most one per assignment and course
 * enrollment. The work itself is in its attempts; nothing submitted is stored here.
 */
@Entity
@Table(name = "lms_assignment_submissions")
public class LmsAssignmentSubmission {

    // No cascade: the assignment and the enrollment are never created or deleted through a submission.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    private LmsAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_enrollment_id", nullable = false, updatable = false)
    private CourseEnrollment courseEnrollment;

    // The number of the newest attempt, or 0 before the first. The attempts themselves are separate rows.
    @Column(name = "latest_attempt_number", nullable = false)
    private int latestAttemptNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsAssignmentSubmission() {
        // Required by JPA.
    }

    public LmsAssignmentSubmission(LmsAssignment assignment, CourseEnrollment courseEnrollment) {
        this.assignment = Require.notNull(assignment, "assignment");
        this.courseEnrollment = Require.notNull(courseEnrollment, "courseEnrollment");
    }

    public Long getId() {
        return id;
    }

    public LmsAssignment getAssignment() {
        return assignment;
    }

    public CourseEnrollment getCourseEnrollment() {
        return courseEnrollment;
    }

    public int getLatestAttemptNumber() {
        return latestAttemptNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Reserves the number of a new attempt: 1 for the first, then one more each time. Callers hold a lock
     * on the student's records while they do this, so two attempts never get the same number.
     */
    public int takeNextAttemptNumber() {
        latestAttemptNumber += 1;
        return latestAttemptNumber;
    }
}
