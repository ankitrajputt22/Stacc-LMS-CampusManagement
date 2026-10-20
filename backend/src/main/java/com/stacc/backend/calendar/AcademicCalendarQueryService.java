package com.stacc.backend.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.stacc.backend.academic.calendar.AcademicCalendarEvent;
import com.stacc.backend.academic.calendar.AcademicCalendarEventQueryService;
import com.stacc.backend.academic.calendar.AcademicCalendarEventResponse;
import com.stacc.backend.academic.calendar.AcademicCalendarScope;
import com.stacc.backend.calendar.MyCalendarResponse.FacultyAssignmentDeadline;
import com.stacc.backend.calendar.MyCalendarResponse.StudentAssignmentDeadline;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.api.LmsCourseSummaryResponse;
import com.stacc.backend.lms.api.LmsFacultyCourseQueryService;
import com.stacc.backend.lms.api.LmsFacultyCourseSummaryResponse;
import com.stacc.backend.lms.api.LmsStudentCourseQueryService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the signed-in account's calendar for a date range, one part per role it holds, in a fixed number of queries.
 * Official events come from {@link AcademicCalendarEventQueryService}: every visible event for an admin, otherwise
 * those of the student's own semesters and the faculty member's taught semesters together. Deadlines are read from
 * {@link LmsAssignment} itself every time, never copied: a student's from the courses
 * {@link LmsStudentCourseQueryService} lists, published only, and a faculty member's from the courses
 * {@link LmsFacultyCourseQueryService} lists, draft and published. No other access rule exists here. It reads only,
 * and stores nothing.
 */
@Service
@Transactional(readOnly = true)
public class AcademicCalendarQueryService {

    /** The longest range one request may ask for, in days, both ends included. */
    static final int MAX_RANGE_DAYS = 366;

    static final String RANGE_ORDER = "The from date must not be after the to date.";
    static final String RANGE_TOO_LONG = "The date range must be at most " + MAX_RANGE_DAYS + " days.";
    static final String RANGE_SUPPORTED = "Dates must be between " + AcademicCalendarEvent.EARLIEST_DATE + " and "
            + AcademicCalendarEvent.LATEST_DATE + ".";
    static final String TIME_ZONE_INVALID = "The time zone must be an IANA time zone, such as Asia/Kolkata.";

    // Region IDs such as Asia/Kolkata or UTC. Offsets such as +05:30 and abbreviations such as IST are not accepted.
    private static final Set<String> TIME_ZONES = Set.copyOf(ZoneId.getAvailableZoneIds());

    private final AcademicCalendarEventQueryService events;
    private final LmsStudentCourseQueryService studentCourses;
    private final LmsFacultyCourseQueryService facultyCourses;
    private final LmsAssignmentRepository assignments;

    public AcademicCalendarQueryService(AcademicCalendarEventQueryService events,
            LmsStudentCourseQueryService studentCourses, LmsFacultyCourseQueryService facultyCourses,
            LmsAssignmentRepository assignments) {
        this.events = events;
        this.studentCourses = studentCourses;
        this.facultyCourses = facultyCourses;
        this.assignments = assignments;
    }

    /**
     * The viewer's calendar from one date to another, both included. The time zone only turns those dates into the
     * moments deadlines are compared with: a deadline belongs to the range when it falls on one of its days there. It
     * decides nothing about access.
     *
     * @param viewer the account in the access token and its roles, never anything the client sends
     */
    public MyCalendarResponse getMyCalendar(CalendarViewer viewer, LocalDate from, LocalDate to, String timeZone) {
        requireRange(from, to);
        ZoneId zone = zoneOf(timeZone);
        // A half-open range: from the first moment of the first day to the first moment after the last day.
        Instant dueFrom = from.atStartOfDay(zone).toInstant();
        Instant dueBefore = to.plusDays(1).atStartOfDay(zone).toInstant();

        return new MyCalendarResponse(
                from,
                to,
                zone.getId(),
                academicEvents(viewer, from, to),
                viewer.student() ? studentDeadlines(viewer.userAccountId(), dueFrom, dueBefore) : List.of(),
                viewer.faculty() ? facultyDeadlines(viewer.userAccountId(), dueFrom, dueBefore) : List.of());
    }

    private List<AcademicCalendarEventResponse> academicEvents(CalendarViewer viewer, LocalDate from, LocalDate to) {
        if (viewer.admin()) {
            return events.findAllVisible(from, to);
        }
        AcademicCalendarScope scope = AcademicCalendarScope.NONE;
        if (viewer.student()) {
            scope = scope.and(events.studentScope(viewer.userAccountId()));
        }
        if (viewer.faculty()) {
            scope = scope.and(events.facultyScope(viewer.userAccountId()));
        }
        return events.findVisible(scope, from, to);
    }

    private List<StudentAssignmentDeadline> studentDeadlines(Long userAccountId, Instant dueFrom, Instant dueBefore) {
        Map<Long, LmsCourseSummaryResponse> courses = new LinkedHashMap<>();
        for (LmsCourseSummaryResponse course : studentCourses.getAccessibleCourses(userAccountId)) {
            courses.putIfAbsent(course.lmsCourseId(), course);
        }
        // Without a current course there is nothing to look up, and no empty IN list is ever sent.
        if (courses.isEmpty()) {
            return List.of();
        }
        return assignments.findAllByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAscIdAsc(
                        courses.keySet(), LmsContentPolicy.PUBLISHED_ONLY, dueFrom, dueBefore)
                .stream()
                .filter(assignment -> assignment.getStatus() == LmsContentStatus.PUBLISHED
                        && courses.containsKey(assignment.getLmsCourse().getId()))
                .sorted(LmsAssignment.BY_DUE_AT)
                .map(assignment -> studentDeadlineOf(assignment, courses.get(assignment.getLmsCourse().getId())))
                .toList();
    }

    private List<FacultyAssignmentDeadline> facultyDeadlines(Long userAccountId, Instant dueFrom, Instant dueBefore) {
        Map<Long, LmsFacultyCourseSummaryResponse> courses = new LinkedHashMap<>();
        for (LmsFacultyCourseSummaryResponse course : facultyCourses.getAccessibleCourses(userAccountId)) {
            courses.putIfAbsent(course.lmsCourseId(), course);
        }
        if (courses.isEmpty()) {
            return List.of();
        }
        return assignments.findAllByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAscIdAsc(
                        courses.keySet(), LmsContentPolicy.IN_USE, dueFrom, dueBefore)
                .stream()
                .filter(assignment -> LmsContentPolicy.isInUse(assignment.getStatus())
                        && courses.containsKey(assignment.getLmsCourse().getId()))
                .sorted(LmsAssignment.BY_DUE_AT)
                .map(assignment -> facultyDeadlineOf(assignment, courses.get(assignment.getLmsCourse().getId())))
                .toList();
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from.isBefore(AcademicCalendarEvent.EARLIEST_DATE) || to.isAfter(AcademicCalendarEvent.LATEST_DATE)) {
            throw badRequest(RANGE_SUPPORTED);
        }
        if (from.isAfter(to)) {
            throw badRequest(RANGE_ORDER);
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw badRequest(RANGE_TOO_LONG);
        }
    }

    private static ZoneId zoneOf(String timeZone) {
        if (timeZone == null || !TIME_ZONES.contains(timeZone)) {
            throw badRequest(TIME_ZONE_INVALID);
        }
        return ZoneId.of(timeZone);
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    // The course details come from the course lists above, so no further query is made per row.
    private static StudentAssignmentDeadline studentDeadlineOf(LmsAssignment assignment, LmsCourseSummaryResponse course) {
        return new StudentAssignmentDeadline(assignment.getId(), course.lmsCourseId(), course.courseCode(),
                course.courseName(), assignment.getTitle(), assignment.getDueAt(), assignment.getMaxMarks());
    }

    private static FacultyAssignmentDeadline facultyDeadlineOf(LmsAssignment assignment,
            LmsFacultyCourseSummaryResponse course) {
        return new FacultyAssignmentDeadline(assignment.getId(), course.lmsCourseId(), course.courseCode(),
                course.courseName(), assignment.getTitle(), assignment.getDueAt(), assignment.getMaxMarks(),
                assignment.getStatus().name());
    }
}
