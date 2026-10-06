package com.stacc.backend.academic.enrollment;

import java.time.Instant;

import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The official record that a student, through a semester enrollment, takes part in one
 * course offering. This is the record that official course access will later depend on.
 *
 * <p>The student, account, semester, course, programme, and session are all reached through
 * the semester enrollment and the course offering, so none of them is stored here again.
 * The status describes the enrollment only. It is not a grade or a result, and "completed"
 * does not mean "passed". An enrollment is never deleted: it ends as completed or withdrawn.
 */
@Entity
@Table(
        name = "course_enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_course_enrollments_enrollment_offering",
                columnNames = {"semester_enrollment_id", "course_offering_id"}))
public class CourseEnrollment {

    // No cascade on either link: the semester enrollment and the course offering are never
    // created or deleted through a course enrollment.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_enrollment_id", nullable = false, updatable = false)
    private SemesterEnrollment semesterEnrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false, updatable = false)
    private CourseOffering courseOffering;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseEnrollmentStatus status = CourseEnrollmentStatus.ENROLLED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseEnrollment() {
        // Required by JPA.
    }

    /**
     * The semester enrollment and course offering say what this enrollment is, so they cannot be
     * changed afterwards. The offering must belong to the same semester as the semester
     * enrollment. That is checked by the feature that creates course enrollments, not here.
     */
    public CourseEnrollment(SemesterEnrollment semesterEnrollment, CourseOffering courseOffering) {
        this.semesterEnrollment = Require.notNull(semesterEnrollment, "semesterEnrollment");
        this.courseOffering = Require.notNull(courseOffering, "courseOffering");
    }

    public Long getId() {
        return id;
    }

    public SemesterEnrollment getSemesterEnrollment() {
        return semesterEnrollment;
    }

    public CourseOffering getCourseOffering() {
        return courseOffering;
    }

    public CourseEnrollmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markCompleted() {
        requireStillEnrolled();
        this.status = CourseEnrollmentStatus.COMPLETED;
    }

    public void withdraw() {
        requireStillEnrolled();
        this.status = CourseEnrollmentStatus.WITHDRAWN;
    }

    private void requireStillEnrolled() {
        if (status != CourseEnrollmentStatus.ENROLLED) {
            throw new IllegalStateException("This course enrollment has already ended");
        }
    }
}
