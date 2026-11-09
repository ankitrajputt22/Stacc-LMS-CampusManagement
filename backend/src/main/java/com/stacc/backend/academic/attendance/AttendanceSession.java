package com.stacc.backend.academic.attendance;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One class meeting of one course offering, for which attendance is taken. It belongs to the official academic
 * record, never to an LMS course, so attendance does not depend on whether an LMS course exists.
 *
 * <p>The faculty profile that created it is kept for the record; the course, semester, programme, and session
 * are reached through the course offering and are not stored again. A session starts open and is finalized
 * once every student is marked; it is never reopened or deleted.
 */
@Entity
@Table(name = "attendance_sessions")
public class AttendanceSession {

    public static final int MAX_TOPIC_LENGTH = 500;
    public static final Instant EARLIEST_HELD_AT = Instant.parse("2000-01-01T00:00:00Z");
    public static final Instant LATEST_HELD_AT = Instant.parse("9999-12-31T00:00:00Z");

    // No cascade on any link: the course offering and the faculty profile are never created or deleted
    // through a session.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false, updatable = false)
    private CourseOffering courseOffering;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_faculty_profile_id", nullable = false, updatable = false)
    private FacultyProfile createdByFacultyProfile;

    @Column(name = "held_at", nullable = false)
    private Instant heldAt;

    @Column(length = MAX_TOPIC_LENGTH)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceSessionStatus status = AttendanceSessionStatus.OPEN;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttendanceSession() {
        // Required by JPA.
    }

    /**
     * A new open session. That the faculty member teaches the offering is checked by the service that
     * creates sessions, not here.
     *
     * @param heldAt when the class took place, an absolute moment
     * @param topic  optional plain text; blank becomes none
     */
    public AttendanceSession(CourseOffering courseOffering, FacultyProfile createdBy, Instant heldAt, String topic) {
        this.courseOffering = Require.notNull(courseOffering, "courseOffering");
        this.createdByFacultyProfile = Require.notNull(createdBy, "createdByFacultyProfile");
        this.heldAt = validHeldAt(heldAt);
        this.topic = Require.optionalText(topic, "topic", MAX_TOPIC_LENGTH);
    }

    /** Whether a class can be recorded as held at this moment: from the year 2000 to 9999. */
    public static boolean isSupportedHeldAt(Instant heldAt) {
        return heldAt != null && !heldAt.isBefore(EARLIEST_HELD_AT) && !heldAt.isAfter(LATEST_HELD_AT);
    }

    /** Changes when the class was held and its topic. Only an open session can be changed. */
    public void reschedule(Instant heldAt, String topic) {
        if (isFinalized()) {
            throw new IllegalStateException("A finalized session cannot be changed");
        }
        this.heldAt = validHeldAt(heldAt);
        this.topic = Require.optionalText(topic, "topic", MAX_TOPIC_LENGTH);
    }

    /**
     * Finalizes the session at the given moment. Finalizing again changes nothing, so the first finalization
     * time is kept. That every student is marked is checked by the service, which can see the records.
     */
    public void markFinalized(Instant at) {
        if (isFinalized()) {
            return;
        }
        // The database keeps microseconds, so the answer shows the same moment as every later read.
        this.finalizedAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
        this.status = AttendanceSessionStatus.FINALIZED;
    }

    public boolean isFinalized() {
        return status == AttendanceSessionStatus.FINALIZED;
    }

    public Long getId() {
        return id;
    }

    public CourseOffering getCourseOffering() {
        return courseOffering;
    }

    public FacultyProfile getCreatedByFacultyProfile() {
        return createdByFacultyProfile;
    }

    public Instant getHeldAt() {
        return heldAt;
    }

    public String getTopic() {
        return topic;
    }

    public AttendanceSessionStatus getStatus() {
        return status;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static Instant validHeldAt(Instant heldAt) {
        Require.notNull(heldAt, "heldAt");
        if (!isSupportedHeldAt(heldAt)) {
            throw new IllegalArgumentException("heldAt must be between " + EARLIEST_HELD_AT + " and " + LATEST_HELD_AT);
        }
        return heldAt.truncatedTo(ChronoUnit.MICROS);
    }
}
