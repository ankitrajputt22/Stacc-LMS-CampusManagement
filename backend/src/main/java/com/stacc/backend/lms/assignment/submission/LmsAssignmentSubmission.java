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

@Entity
@Table(name = "lms_assignment_submissions")
public class LmsAssignmentSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    private LmsAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_enrollment_id", nullable = false, updatable = false)
    private CourseEnrollment courseEnrollment;

    @Column(name = "latest_attempt_number", nullable = false)
    private int latestAttemptNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsAssignmentSubmission() {
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

    public int takeNextAttemptNumber() {
        latestAttemptNumber += 1;
        return latestAttemptNumber;
    }
}
