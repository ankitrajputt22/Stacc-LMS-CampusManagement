package com.stacc.backend.lms.course;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The LMS learning space for one official course offering. The offering stays academic data
 * in the academic module; this is only the place where its teaching and learning will happen.
 *
 * <p>The course, semester, programme, academic session, and department are all reached
 * through the course offering, so none of them is stored here again. A published space is
 * not open to every student: access will depend on official course enrollment. A space that
 * is no longer used is archived rather than deleted.
 */
@Entity
@Table(name = "lms_courses")
public class LmsCourse {

    // No cascade: a course offering is official academic data and is never created or
    // deleted through its learning space.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false, unique = true, updatable = false)
    private CourseOffering courseOffering;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmsCourseStatus status = LmsCourseStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsCourse() {
        // Required by JPA.
    }

    /** The course offering says what this learning space is for, so it cannot be changed afterwards. */
    public LmsCourse(CourseOffering courseOffering) {
        this.courseOffering = Require.notNull(courseOffering, "courseOffering");
    }

    public Long getId() {
        return id;
    }

    public CourseOffering getCourseOffering() {
        return courseOffering;
    }

    public LmsCourseStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void publish() {
        if (status == LmsCourseStatus.ARCHIVED) {
            throw new IllegalStateException("An archived LMS course cannot be published again");
        }
        this.status = LmsCourseStatus.PUBLISHED;
    }

    public void archive() {
        this.status = LmsCourseStatus.ARCHIVED;
    }
}
