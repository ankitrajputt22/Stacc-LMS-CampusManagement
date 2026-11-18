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

@Entity
@Table(name = "attendance_sessions")
public class AttendanceSession {

    public static final int MAX_TOPIC_LENGTH = 500;
    public static final Instant EARLIEST_HELD_AT = Instant.parse("2000-01-01T00:00:00Z");
    public static final Instant LATEST_HELD_AT = Instant.parse("9999-12-31T00:00:00Z");

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
    }

    public AttendanceSession(CourseOffering courseOffering, FacultyProfile createdBy, Instant heldAt, String topic) {
        this.courseOffering = Require.notNull(courseOffering, "courseOffering");
        this.createdByFacultyProfile = Require.notNull(createdBy, "createdByFacultyProfile");
        this.heldAt = validHeldAt(heldAt);
        this.topic = Require.optionalText(topic, "topic", MAX_TOPIC_LENGTH);
    }

    public static boolean isSupportedHeldAt(Instant heldAt) {
        return heldAt != null && !heldAt.isBefore(EARLIEST_HELD_AT) && !heldAt.isAfter(LATEST_HELD_AT);
    }

    public void reschedule(Instant heldAt, String topic) {
        if (isFinalized()) {
            throw new IllegalStateException("A finalized session cannot be changed");
        }
        this.heldAt = validHeldAt(heldAt);
        this.topic = Require.optionalText(topic, "topic", MAX_TOPIC_LENGTH);
    }

    public void markFinalized(Instant at) {
        if (isFinalized()) {
            return;
        }
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
