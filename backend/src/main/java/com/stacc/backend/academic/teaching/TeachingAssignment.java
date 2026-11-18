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

@Entity
@Table(
        name = "teaching_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_teaching_assignments_faculty_offering",
                columnNames = {"faculty_profile_id", "course_offering_id"}))
public class TeachingAssignment {

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
    @Column(name = "assignment_role", nullable = false, length = 20)
    private TeachingRole role = TeachingRole.PRIMARY;

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
    }

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

    public TeachingRole getRole() {
        return role;
    }

    public void changeRole(TeachingRole role) {
        this.role = Require.notNull(role, "role");
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
