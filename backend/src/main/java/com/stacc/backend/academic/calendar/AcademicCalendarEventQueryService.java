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

@Service
@Transactional(readOnly = true)
public class AcademicCalendarEventQueryService {

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

    public List<AcademicCalendarEventResponse> findVisible(AcademicCalendarScope scope, LocalDate from, LocalDate to) {
        if (scope.semesterIds().isEmpty() || scope.academicSessionIds().isEmpty()) {
            return List.of();
        }
        return events.findVisibleInRangeForScope(from, to, scope.semesterIds(), scope.academicSessionIds()).stream()
                .filter(AcademicCalendarEvent::isVisibleToUsers)
                .filter(event -> appliesTo(event, scope))
                .map(AcademicCalendarEventResponse::of)
                .toList();
    }

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
