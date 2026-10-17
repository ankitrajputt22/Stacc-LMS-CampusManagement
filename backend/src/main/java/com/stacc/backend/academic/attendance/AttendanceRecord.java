package com.stacc.backend.academic.attendance;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
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
 * One student's attendance in one session, through the official course enrollment that was current when the
 * session was created. The student, account, and course are reached through the enrollment and are not stored
 * again. A later change to the enrollment, such as a withdrawal, never removes the record.
 *
 * <p>A record starts {@link AttendanceStatus#UNMARKED}, never absent. Once it is present or absent, the faculty
 * profile that last changed it and when are kept.
 */
@Entity
@Table(
        name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_records_session_enrollment",
                columnNames = {"attendance_session_id", "course_enrollment_id"}))
public class AttendanceRecord {

    // No cascade on any link: sessions, enrollments, and faculty profiles are never created or deleted
    // through a record.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_session_id", nullable = false, updatable = false)
    private AttendanceSession attendanceSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_enrollment_id", nullable = false, updatable = false)
    private CourseEnrollment courseEnrollment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status = AttendanceStatus.UNMARKED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by_faculty_profile_id")
    private FacultyProfile markedByFacultyProfile;

    @Column(name = "marked_at")
    private Instant markedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttendanceRecord() {
        // Required by JPA.
    }

    /** An unmarked record. Which enrollments get one is decided by the service that creates the session. */
    public AttendanceRecord(AttendanceSession attendanceSession, CourseEnrollment courseEnrollment) {
        this.attendanceSession = Require.notNull(attendanceSession, "attendanceSession");
        this.courseEnrollment = Require.notNull(courseEnrollment, "courseEnrollment");
    }

    /**
     * Sets the status. Marking present or absent records who did it and when; setting the same status again
     * changes nothing, so the last real change stays recorded. Going back to unmarked clears both, and is only
     * possible while the session is open: finalized attendance can only be corrected between present and absent.
     *
     * @return whether anything changed
     */
    public boolean mark(AttendanceStatus newStatus, FacultyProfile by, Instant at) {
        Require.notNull(newStatus, "status");
        if (newStatus == status) {
            return false;
        }
        if (newStatus == AttendanceStatus.UNMARKED) {
            if (attendanceSession.isFinalized()) {
                throw new IllegalStateException("Finalized attendance cannot be unmarked");
            }
            this.status = AttendanceStatus.UNMARKED;
            this.markedByFacultyProfile = null;
            this.markedAt = null;
            return true;
        }
        this.markedByFacultyProfile = Require.notNull(by, "markedBy");
        // The database keeps microseconds, so the answer shows the same moment as every later read.
        this.markedAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
        this.status = newStatus;
        return true;
    }

    public boolean isMarked() {
        return status != AttendanceStatus.UNMARKED;
    }

    public Long getId() {
        return id;
    }

    public AttendanceSession getAttendanceSession() {
        return attendanceSession;
    }

    public CourseEnrollment getCourseEnrollment() {
        return courseEnrollment;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public FacultyProfile getMarkedByFacultyProfile() {
        return markedByFacultyProfile;
    }

    public Instant getMarkedAt() {
        return markedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
