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

@Entity
@Table(
        name = "course_enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_course_enrollments_enrollment_offering",
                columnNames = {"semester_enrollment_id", "course_offering_id"}))
public class CourseEnrollment {

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

    @Column(name = "dropped_at")
    private Instant droppedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseEnrollment() {
    }

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

    public Instant getDroppedAt() {
        return droppedAt;
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

    public void drop(Instant moment) {
        requireStillEnrolled();
        this.status = CourseEnrollmentStatus.DROPPED;
        this.droppedAt = Require.notNull(moment, "moment");
    }

    private void requireStillEnrolled() {
        if (status != CourseEnrollmentStatus.ENROLLED) {
            throw new IllegalStateException("This course enrollment has already ended");
        }
    }
}
