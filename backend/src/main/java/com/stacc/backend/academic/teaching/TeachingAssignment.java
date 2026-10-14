package com.stacc.backend.academic.teaching;

import java.time.Instant;

import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.identity.faculty.FacultyProfile;
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
 * The official record that a faculty member teaches one course offering: one course in one
 * semester, never the permanent course. A faculty member can teach many offerings, and an
 * offering can have several faculty members, but each pair is recorded only once.
 *
 * <p>The account, department, course, semester, programme, and session are all reached through
 * the faculty profile and the course offering, so none of them is stored here again. The faculty
 * member's primary department does not have to own the course. An assignment that has ended is
 * made inactive rather than deleted.
 */
@Entity
@Table(
        name = "teaching_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_teaching_assignments_faculty_offering",
                columnNames = {"faculty_profile_id", "course_offering_id"}))
public class TeachingAssignment {

    // No cascade on either link: the faculty profile and the course offering are never created
    // or deleted through a teaching assignment.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_profile_id", nullable = false, updatable = false)
    private FacultyProfile facultyProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false, updatable = false)
    private CourseOffering courseOffering;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeachingAssignmentStatus status = TeachingAssignmentStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TeachingAssignment() {
        // Required by JPA.
    }

    /**
     * The faculty profile and course offering say what this assignment is, so they cannot be
     * changed afterwards. The faculty profile should be active when an assignment is created or
     * made active again. That is checked by the feature that manages assignments, not here.
     */
    public TeachingAssignment(FacultyProfile facultyProfile, CourseOffering courseOffering) {
        this.facultyProfile = Require.notNull(facultyProfile, "facultyProfile");
        this.courseOffering = Require.notNull(courseOffering, "courseOffering");
    }

    public Long getId() {
        return id;
    }

    public FacultyProfile getFacultyProfile() {
        return facultyProfile;
    }

    public CourseOffering getCourseOffering() {
        return courseOffering;
    }

    public TeachingAssignmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void activate() {
        this.status = TeachingAssignmentStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = TeachingAssignmentStatus.INACTIVE;
    }
}
