package com.stacc.backend.academic.enrollment;

import java.time.Instant;

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

/**
 * The official record that one student takes part in one semester. A student has one of
 * these for each semester over the years, which is how the academic history is kept.
 *
 * <p>The account, programme, department, and academic session are all reached through the
 * student profile and the semester, so none of them is stored here again. An enrollment is
 * never deleted: it ends as completed or withdrawn.
 */
@Entity
@Table(
        name = "semester_enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_semester_enrollments_student_semester",
                columnNames = {"student_profile_id", "semester_id"}))
public class SemesterEnrollment {

    // No cascade on either link: the student profile and the semester are never created or
    // deleted through an enrollment.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false, updatable = false)
    private StudentProfile studentProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false, updatable = false)
    private Semester semester;

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
        // Required by JPA.
    }

    /**
     * The student and semester say what this enrollment is, so they cannot be changed afterwards.
     * Whether the student's programme matches the semester's programme is checked by the feature
     * that creates enrollments, not here.
     */
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
