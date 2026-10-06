package com.stacc.backend.lms.membership;

import java.time.Instant;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.course.LmsCourse;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The LMS-side membership of one student in one LMS course. It is based on one official
 * course enrollment and never replaces it: the course enrollment stays the record of who
 * officially takes the course.
 *
 * <p>The student, account, course offering, course, semester, programme, and session are all
 * reached through the course enrollment and the LMS course, so none of them is stored here
 * again. An active membership does not by itself give access to the course. A membership
 * that is no longer in use is made inactive rather than deleted.
 */
@Entity
@Table(name = "lms_student_memberships")
public class LmsStudentMembership {

    // No cascade on either link: the official course enrollment and the LMS course are never
    // created or deleted through a membership.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_enrollment_id", nullable = false, unique = true, updatable = false)
    private CourseEnrollment courseEnrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmsStudentMembershipStatus status = LmsStudentMembershipStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsStudentMembership() {
        // Required by JPA.
    }

    /**
     * The course enrollment and LMS course say what this membership is, so they cannot be
     * changed afterwards. Both must belong to the same course offering. That is checked by the
     * feature that creates memberships, not here.
     */
    public LmsStudentMembership(CourseEnrollment courseEnrollment, LmsCourse lmsCourse) {
        this.courseEnrollment = Require.notNull(courseEnrollment, "courseEnrollment");
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
    }

    public Long getId() {
        return id;
    }

    public CourseEnrollment getCourseEnrollment() {
        return courseEnrollment;
    }

    public LmsCourse getLmsCourse() {
        return lmsCourse;
    }

    public LmsStudentMembershipStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void activate() {
        this.status = LmsStudentMembershipStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = LmsStudentMembershipStatus.INACTIVE;
    }
}
