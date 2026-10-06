package com.stacc.backend.academic.offering;

import java.time.Instant;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.semester.Semester;
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
 * One course being offered in one semester. The programme and academic session come from
 * the semester, and the owning department comes from the course, so none of them is stored
 * here again. A finished offering is closed rather than deleted.
 */
@Entity
@Table(
        name = "course_offerings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_course_offerings_course_semester",
                columnNames = {"course_id", "semester_id"}))
public class CourseOffering {

    // No cascade on either link: courses and semesters are shared data and are never
    // created or deleted through an offering.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, updatable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false, updatable = false)
    private Semester semester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseOfferingStatus status = CourseOfferingStatus.PLANNED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseOffering() {
        // Required by JPA.
    }

    /** The course and semester say what this offering is, so they cannot be changed afterwards. */
    public CourseOffering(Course course, Semester semester) {
        this.course = Require.notNull(course, "course");
        this.semester = Require.notNull(semester, "semester");
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public Semester getSemester() {
        return semester;
    }

    public CourseOfferingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markActive() {
        if (status == CourseOfferingStatus.CLOSED) {
            throw new IllegalStateException("A closed course offering cannot be made active again");
        }
        this.status = CourseOfferingStatus.ACTIVE;
    }

    public void close() {
        this.status = CourseOfferingStatus.CLOSED;
    }
}
