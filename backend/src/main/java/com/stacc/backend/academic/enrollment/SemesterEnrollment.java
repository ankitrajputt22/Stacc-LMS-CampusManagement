package com.stacc.backend.academic.enrollment;

import java.time.Instant;

import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.identity.student.StudentProfile;
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
        name = "semester_enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_semester_enrollments_student_semester",
                columnNames = {"student_profile_id", "semester_id"}))
public class SemesterEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false, updatable = false)
    private StudentProfile studentProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false, updatable = false)
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SemesterEnrollmentStatus status = SemesterEnrollmentStatus.ENROLLED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SemesterEnrollment() {
    }

    public SemesterEnrollment(StudentProfile studentProfile, Semester semester) {
        this.studentProfile = Require.notNull(studentProfile, "studentProfile");
        this.semester = Require.notNull(semester, "semester");
    }

    public Long getId() {
        return id;
    }

    public StudentProfile getStudentProfile() {
        return studentProfile;
    }

    public Semester getSemester() {
        return semester;
    }

    public Section getSection() {
        return section;
    }

    public void placeIn(Section section) {
        if (section != null && !section.getSemester().getId().equals(semester.getId())) {
            throw new IllegalArgumentException("section must belong to the enrollment's semester");
        }
        this.section = section;
    }

    public SemesterEnrollmentStatus getStatus() {
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
        this.status = SemesterEnrollmentStatus.COMPLETED;
    }

    public void withdraw() {
        requireStillEnrolled();
        this.status = SemesterEnrollmentStatus.WITHDRAWN;
    }

    private void requireStillEnrolled() {
        if (status != SemesterEnrollmentStatus.ENROLLED) {
            throw new IllegalStateException("This semester enrollment has already ended");
        }
    }
}
