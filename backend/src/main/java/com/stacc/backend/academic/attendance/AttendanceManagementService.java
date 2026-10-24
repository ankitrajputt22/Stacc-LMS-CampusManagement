package com.stacc.backend.academic.attendance;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faculty take attendance here: create a session, mark the students, finalize it, and correct it afterwards.
 * Every action starts from the signed-in account and checks {@link FacultyTeachingAccessService} for the
 * course offering, found from the stored session when there is one, never from the request. The acting faculty
 * profile comes from that check. A course offering or session the caller may not use answers the same 404 as
 * one that does not exist. No LMS data is read or needed.
 *
 * <p>Sessions and records are never deleted, and a finalized session is never reopened.
 */
@Service
@Transactional
public class AttendanceManagementService {

    static final String COURSE_UNAVAILABLE = "Course offering not found or unavailable.";
    static final String SESSION_UNAVAILABLE = "Attendance session not found or unavailable.";
    static final String FINALIZED_NOT_EDITABLE = "A finalized session's date and topic can no longer be changed.";
    static final String DUPLICATE_RECORD = "Each attendance record can be sent only once.";
    static final String FOREIGN_RECORD = "Every attendance record must belong to this session.";
    static final String FINALIZED_UNMARK = "Finalized attendance can only be corrected to present or absent.";
    static final String NOT_ALL_MARKED = "All students must be marked before finalizing attendance.";

    /** One requested change: the record, by the ID the server gave it, and its new status. */
    public record RecordChange(Long attendanceRecordId, AttendanceStatus status) {
    }

    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final CourseEnrollmentRepository courseEnrollments;
    private final FacultyTeachingAccessService teachingAccess;
    private final Clock clock;
    private final NotificationService notifications;

    public AttendanceManagementService(
            AttendanceSessionRepository sessions,
            AttendanceRecordRepository records,
            CourseEnrollmentRepository courseEnrollments,
            FacultyTeachingAccessService teachingAccess,
            Clock clock,
            NotificationService notifications) {
        this.sessions = sessions;
        this.records = records;
        this.courseEnrollments = courseEnrollments;
        this.teachingAccess = teachingAccess;
        this.clock = clock;
        this.notifications = notifications;
    }

    /**
     * Creates an open session and, in the same transaction, one unmarked record for every course enrollment of
     * the offering that is enrolled right now. That roster is a snapshot: later enrollments and withdrawals never
     * change it. The client never chooses the students.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public AttendanceSession createSession(Long userAccountId, Long courseOfferingId, Instant heldAt, String topic) {
        TeachingAssignment teaching = teachingAccess.findCurrentTeachingAssignment(userAccountId, courseOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));

        AttendanceSession session = sessions.save(
                new AttendanceSession(teaching.getCourseOffering(), teaching.getFacultyProfile(), heldAt, topic));
        List<CourseEnrollment> enrolled = courseEnrollments.findAllByCourseOfferingIdAndStatus(
                courseOfferingId, CourseEnrollmentStatus.ENROLLED);
        records.saveAll(enrolled.stream().map(enrollment -> new AttendanceRecord(session, enrollment)).toList());
        records.flush();
        return session;
    }

    /** Changes when an open session's class was held and its topic. A finalized session answers 409. */
    public AttendanceSession updateSession(Long userAccountId, Long sessionId, Instant heldAt, String topic) {
        AttendanceSession session = lockedSession(sessionId);
        actingFaculty(userAccountId, session);
        if (session.isFinalized()) {
            throw new ApiException(HttpStatus.CONFLICT, FINALIZED_NOT_EDITABLE);
        }
        session.reschedule(heldAt, topic);
        return sessions.saveAndFlush(session);
    }

    /**
     * Saves the given statuses. Records are named by their own IDs and must all belong to this session, each
     * once. An open session takes any status, so attendance can be saved part of the way; a finalized one only
     * takes corrections between present and absent. A record whose status changes to present or absent records
     * the acting faculty profile and the time; the session's finalization time never changes.
     */
    public AttendanceSession saveRecords(Long userAccountId, Long sessionId, List<RecordChange> changes) {
        AttendanceSession session = lockedSession(sessionId);
        FacultyProfile actor = actingFaculty(userAccountId, session);

        Set<Long> seen = new HashSet<>();
        for (RecordChange change : changes) {
            if (!seen.add(change.attendanceRecordId())) {
                throw badRequest(DUPLICATE_RECORD);
            }
        }
        Map<Long, AttendanceRecord> roster = records.findAllWithStudentBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(AttendanceRecord::getId, Function.identity()));
        if (!roster.keySet().containsAll(seen)) {
            throw badRequest(FOREIGN_RECORD);
        }
        if (session.isFinalized() && changes.stream().anyMatch(change -> change.status() == AttendanceStatus.UNMARKED)) {
            throw badRequest(FINALIZED_UNMARK);
        }

        Instant now = clock.instant();
        List<AttendanceRecord> corrected = new ArrayList<>();
        for (RecordChange change : changes) {
            AttendanceRecord record = roster.get(change.attendanceRecordId());
            AttendanceStatus before = record.getStatus();
            record.mark(change.status(), actor, now);
            // Marking an open session is ordinary work; only a real correction of attendance students already see
            // tells the student.
            if (session.isFinalized() && record.getStatus() != before) {
                corrected.add(record);
            }
        }
        records.flush();
        corrected.forEach(record -> notifyCorrection(session, record));
        return session;
    }

    private void notifyCorrection(AttendanceSession session, AttendanceRecord record) {
        Course course = session.getCourseOffering().getCourse();
        Long student = record.getCourseEnrollment().getSemesterEnrollment().getStudentProfile().getUserAccount().getId();
        String status = record.getStatus() == AttendanceStatus.PRESENT ? "Present" : "Absent";
        notifications.notifyUser(new NotificationMessage(NotificationType.ATTENDANCE_UPDATED, "Attendance corrected",
                "Your attendance for a " + course.getCode() + " " + course.getName() + " class was corrected to " + status + ".",
                NotificationSourceType.ATTENDANCE_RECORD, record.getId(), "/academic/attendance"), student);
    }

    /**
     * Finalizes an open session once every student on its roster is present or absent; students then see it.
     * Finalizing a finalized session changes nothing and keeps its first finalization time.
     */
    public AttendanceSession finalizeSession(Long userAccountId, Long sessionId) {
        AttendanceSession session = lockedSession(sessionId);
        actingFaculty(userAccountId, session);
        if (session.isFinalized()) {
            return session;
        }
        if (records.countByAttendanceSessionIdAndStatus(session.getId(), AttendanceStatus.UNMARKED) > 0) {
            throw badRequest(NOT_ALL_MARKED);
        }
        session.markFinalized(clock.instant());
        return sessions.saveAndFlush(session);
    }

    // The lock comes before the access check, so the check and the change see the same session.
    private AttendanceSession lockedSession(Long sessionId) {
        if (sessionId == null) {
            throw sessionUnavailable();
        }
        return sessions.findByIdForUpdate(sessionId).orElseThrow(AttendanceManagementService::sessionUnavailable);
    }

    private FacultyProfile actingFaculty(Long userAccountId, AttendanceSession session) {
        return teachingAccess.findCurrentTeachingAssignment(userAccountId, session.getCourseOffering().getId())
                .map(TeachingAssignment::getFacultyProfile)
                .orElseThrow(AttendanceManagementService::sessionUnavailable);
    }

    private static ApiException sessionUnavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, SESSION_UNAVAILABLE);
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
