package com.stacc.backend.academic.calendar;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository.EnrolledSemester;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which official calendar events someone sees, decided from academic records alone: no LMS course is needed. Users
 * only ever get events that {@link AcademicCalendarEvent#isVisibleToUsers()}: published ones, and cancelled ones
 * that were published first. Who they apply to comes from the signed-in account: a student's own semester
 * enrollments, or a faculty member's current teaching assignments. It only reads.
 */
@Service
@Transactional(readOnly = true)
public class AcademicCalendarEventQueryService {

    /**
     * The semester enrollments whose events a student sees: current ones, and completed ones, so history stays on the
     * calendar. A withdrawn enrollment does not count.
     */
    static final Set<SemesterEnrollmentStatus> CALENDAR_PARTICIPATION =
            Set.of(SemesterEnrollmentStatus.ENROLLED, SemesterEnrollmentStatus.COMPLETED);

    private final AcademicCalendarEventRepository events;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final TeachingAssignmentRepository teachingAssignments;

    public AcademicCalendarEventQueryService(AcademicCalendarEventRepository events,
            SemesterEnrollmentRepository semesterEnrollments, TeachingAssignmentRepository teachingAssignments) {
        this.events = events;
        this.semesterEnrollments = semesterEnrollments;
        this.teachingAssignments = teachingAssignments;
    }

    /**
     * The semesters a student takes part in, through their own enrollments that are enrolled or completed, and the
     * sessions of those semesters. The student profile's status does not matter: like attendance and results, this is
     * the student's own academic history.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public AcademicCalendarScope studentScope(Long userAccountId) {
        if (userAccountId == null) {
            return AcademicCalendarScope.NONE;
        }
        Set<Long> semesterIds = new HashSet<>();
        Set<Long> sessionIds = new HashSet<>();
        for (EnrolledSemester enrolled : semesterEnrollments.findEnrolledSemestersByUserAccountId(
                userAccountId, CALENDAR_PARTICIPATION)) {
            semesterIds.add(enrolled.getSemesterId());
            sessionIds.add(enrolled.getAcademicSessionId());
        }
        return new AcademicCalendarScope(semesterIds, sessionIds);
    }

    /**
     * The semesters a faculty member teaches in now, through teaching assignments that count under
     * {@link FacultyTeachingAccessService#isCurrentTeaching} (an active faculty profile and an active assignment),
     * and the sessions of those semesters. No LMS course is needed.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public AcademicCalendarScope facultyScope(Long userAccountId) {
        if (userAccountId == null) {
            return AcademicCalendarScope.NONE;
        }
        Set<Long> semesterIds = new HashSet<>();
        Set<Long> sessionIds = new HashSet<>();
        for (TeachingAssignment assignment : teachingAssignments.findAllWithCourseDetailsByUserAccountId(userAccountId)) {
            if (FacultyTeachingAccessService.isCurrentTeaching(assignment, userAccountId)) {
                Semester semester = assignment.getCourseOffering().getSemester();
                semesterIds.add(semester.getId());
                sessionIds.add(semester.getAcademicSession().getId());
            }
        }
        return new AcademicCalendarScope(semesterIds, sessionIds);
    }

    /**
     * The visible events in the date range that apply to the scope: those of its semesters, and the session-wide
     * events of its sessions. The earliest start first. An empty scope sends no query.
     */
    public List<AcademicCalendarEventResponse> findVisible(AcademicCalendarScope scope, LocalDate from, LocalDate to) {
        // Both sets come from the same semesters, so neither is empty unless both are, and no empty IN list is sent.
        if (scope.semesterIds().isEmpty() || scope.academicSessionIds().isEmpty()) {
            return List.of();
        }
        return events.findVisibleInRangeForScope(from, to, scope.semesterIds(), scope.academicSessionIds()).stream()
                .filter(AcademicCalendarEvent::isVisibleToUsers)
                .filter(event -> appliesTo(event, scope))
                .map(AcademicCalendarEventResponse::of)
                .toList();
    }

    /** Every visible event in the date range, whatever its session or semester, for the admin calendar. */
    public List<AcademicCalendarEventResponse> findAllVisible(LocalDate from, LocalDate to) {
        return events.findVisibleInRange(from, to).stream()
                .filter(AcademicCalendarEvent::isVisibleToUsers)
                .map(AcademicCalendarEventResponse::of)
                .toList();
    }

    private static boolean appliesTo(AcademicCalendarEvent event, AcademicCalendarScope scope) {
        Semester semester = event.getSemester();
        return semester == null
                ? scope.academicSessionIds().contains(event.getAcademicSession().getId())
                : scope.semesterIds().contains(semester.getId());
    }
}
