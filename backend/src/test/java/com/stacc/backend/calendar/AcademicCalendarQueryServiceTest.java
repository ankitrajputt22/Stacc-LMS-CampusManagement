package com.stacc.backend.calendar;

import static com.stacc.backend.academic.calendar.CalendarFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.stacc.backend.academic.calendar.AcademicCalendarEventQueryService;
import com.stacc.backend.academic.calendar.AcademicCalendarEventResponse;
import com.stacc.backend.academic.calendar.AcademicCalendarScope;
import com.stacc.backend.academic.calendar.CalendarFixtures;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.offering.CourseOffering;
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
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AcademicCalendarQueryServiceTest {

    private static final long ACCOUNT = 70;
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);
    private static final LocalDate OCT_21 = LocalDate.of(2026, 10, 21);
    private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);
    private static final String KOLKATA = "Asia/Kolkata";

    private final CalendarFixtures college = new CalendarFixtures();
    private final AcademicCalendarEventQueryService events = mock(AcademicCalendarEventQueryService.class);
    private final LmsStudentCourseQueryService studentCourses = mock(LmsStudentCourseQueryService.class);
    private final LmsFacultyCourseQueryService facultyCourses = mock(LmsFacultyCourseQueryService.class);
    private final LmsAssignmentRepository assignments = mock(LmsAssignmentRepository.class);
    private final AcademicCalendarQueryService service =
            new AcademicCalendarQueryService(events, studentCourses, facultyCourses, assignments);

    private final LmsCourse dataStructures = lmsCourse(42, "CS301");
    private final LmsCourse databases = lmsCourse(43, "CS302");
    private final LmsCourse someoneElses = lmsCourse(44, "CS303");

    private final AcademicCalendarScope studentScope = new AcademicCalendarScope(Set.of(9L), Set.of(2L));
    private final AcademicCalendarScope facultyScope = new AcademicCalendarScope(Set.of(10L), Set.of(2L));
    private final AcademicCalendarEventResponse holiday = new AcademicCalendarEventResponse(1L, "Diwali holiday", null,
            "HOLIDAY", OCT_20, OCT_20, "PUBLISHED", "2026-27", null, null);

    @BeforeEach
    void noEnrollmentsOrTeachingUnlessATestSaysSo() {
        when(events.studentScope(anyLong())).thenReturn(AcademicCalendarScope.NONE);
        when(events.facultyScope(anyLong())).thenReturn(AcademicCalendarScope.NONE);
    }

    private LmsCourse lmsCourse(long id, String code) {
        Course course = withId(new Course(college.cse, code, "Course " + code, new BigDecimal("4.00")), id + 100);
        return withId(new LmsCourse(withId(new CourseOffering(course, college.third), id + 200)), id);
    }

    private static LmsAssignment assignment(long id, LmsCourse course, String dueAt, LmsContentStatus status) {
        LmsAssignment assignment = withId(new LmsAssignment(course, "Assignment " + id, "Do the work.",
                Instant.parse(dueAt), new BigDecimal("20.00")), id);
        if (status != LmsContentStatus.DRAFT) {
            assignment.publish(Instant.parse("2026-09-01T00:00:00Z"));
        }
        if (status == LmsContentStatus.ARCHIVED) {
            assignment.archive();
        }
        return assignment;
    }

    private static LmsCourseSummaryResponse studentCourse(LmsCourse course) {
        return new LmsCourseSummaryResponse(course.getId(), course.getCourseOffering().getCourse().getCode(),
                course.getCourseOffering().getCourse().getName(), new BigDecimal("4.00"), 3, "BTECH-CSE", "2026-27");
    }

    private static LmsFacultyCourseSummaryResponse facultyCourse(LmsCourse course, String status) {
        return new LmsFacultyCourseSummaryResponse(course.getId(), course.getCourseOffering().getCourse().getCode(),
                course.getCourseOffering().getCourse().getName(), new BigDecimal("4.00"), 3, "BTECH-CSE", "2026-27", status);
    }

    /** Answers the deadline query the way the database would: by course, status, and the half-open range. */
    @SuppressWarnings("unchecked")
    private void deadlinesStored(LmsAssignment... stored) {
        when(assignments.findAllByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAscIdAsc(
                anyCollection(), anyCollection(), any(), any())).thenAnswer(call -> {
                    Collection<Long> courseIds = call.getArgument(0);
                    Collection<LmsContentStatus> statuses = call.getArgument(1);
                    Instant from = call.getArgument(2);
                    Instant before = call.getArgument(3);
                    return List.of(stored).stream()
                            .filter(a -> courseIds.contains(a.getLmsCourse().getId()) && statuses.contains(a.getStatus()))
                            .filter(a -> !a.getDueAt().isBefore(from) && a.getDueAt().isBefore(before))
                            .sorted(LmsAssignment.BY_DUE_AT)
                            .toList();
                });
    }

    private static void assertBadRequest(Runnable call, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class,
                        refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessage(message);
    }

    @Test
    void theRangeMustBeInOrderAtMost366DaysAndInSupportedYears() {
        CalendarViewer student = new CalendarViewer(ACCOUNT, true, false, false);

        assertBadRequest(() -> service.getMyCalendar(student, OCT_31, OCT_1, KOLKATA), AcademicCalendarQueryService.RANGE_ORDER);
        assertBadRequest(() -> service.getMyCalendar(student, OCT_1, OCT_1.plusDays(366), KOLKATA),
                AcademicCalendarQueryService.RANGE_TOO_LONG);
        assertBadRequest(() -> service.getMyCalendar(student, LocalDate.of(1999, 12, 1), OCT_1, KOLKATA),
                AcademicCalendarQueryService.RANGE_SUPPORTED);
        assertBadRequest(() -> service.getMyCalendar(student, OCT_1, LocalDate.of(10000, 1, 1), KOLKATA),
                AcademicCalendarQueryService.RANGE_SUPPORTED);
        verifyNoInteractions(events, studentCourses, facultyCourses, assignments);

        assertThat(service.getMyCalendar(student, OCT_20, OCT_20, KOLKATA).fromDate()).isEqualTo(OCT_20);
        assertThat(service.getMyCalendar(student, OCT_1, OCT_31, KOLKATA).toDate()).isEqualTo(OCT_31);
        assertThat(service.getMyCalendar(student, OCT_1, OCT_1.plusDays(365), KOLKATA).toDate())
                .isEqualTo(OCT_1.plusDays(365));
    }

    @Test
    void theTimeZoneMustBeAnIanaRegion() {
        CalendarViewer student = new CalendarViewer(ACCOUNT, true, false, false);
        for (String invalid : new String[] {"Mars/Olympus", "+05:30", "IST", "asia/kolkata", "", null}) {
            assertBadRequest(() -> service.getMyCalendar(student, OCT_1, OCT_31, invalid),
                    AcademicCalendarQueryService.TIME_ZONE_INVALID);
        }

        assertThat(service.getMyCalendar(student, OCT_1, OCT_31, "UTC").timeZone()).isEqualTo("UTC");
        assertThat(service.getMyCalendar(student, OCT_1, OCT_31, "America/New_York").timeZone())
                .isEqualTo("America/New_York");
    }

    @Test
    void aDeadlineNearMidnightLandsOnTheDayOfTheRequestedTimeZone() {
        when(studentCourses.getAccessibleCourses(ACCOUNT)).thenReturn(List.of(studentCourse(dataStructures)));
        // 23:59 on 20 October in India, and one minute past midnight on 21 October there.
        LmsAssignment lastMinute = assignment(1, dataStructures, "2026-10-20T18:29:00Z", LmsContentStatus.PUBLISHED);
        LmsAssignment pastMidnight = assignment(2, dataStructures, "2026-10-20T18:31:00Z", LmsContentStatus.PUBLISHED);
        deadlinesStored(lastMinute, pastMidnight);
        CalendarViewer student = new CalendarViewer(ACCOUNT, true, false, false);

        assertThat(service.getMyCalendar(student, OCT_20, OCT_20, KOLKATA).studentAssignmentDeadlines())
                .extracting(StudentAssignmentDeadline::assignmentId).containsExactly(1L);
        assertThat(service.getMyCalendar(student, OCT_21, OCT_21, KOLKATA).studentAssignmentDeadlines())
                .extracting(StudentAssignmentDeadline::assignmentId).containsExactly(2L);
        // In New York both are on the afternoon of 20 October.
        assertThat(service.getMyCalendar(student, OCT_20, OCT_20, "America/New_York").studentAssignmentDeadlines())
                .extracting(StudentAssignmentDeadline::assignmentId).containsExactly(1L, 2L);
        assertThat(service.getMyCalendar(student, OCT_21, OCT_21, "America/New_York").studentAssignmentDeadlines())
                .isEmpty();
    }

    @Test
    void theDaysAreTurnedIntoAHalfOpenRangeOfMoments() {
        when(studentCourses.getAccessibleCourses(ACCOUNT)).thenReturn(List.of(studentCourse(dataStructures)));
        CalendarViewer student = new CalendarViewer(ACCOUNT, true, false, false);

        service.getMyCalendar(student, OCT_20, OCT_20, KOLKATA);
        // The day daylight saving time ends in New York has 25 hours.
        service.getMyCalendar(student, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), "America/New_York");

        verify(assignments).findAllByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAscIdAsc(
                Set.of(42L), LmsContentPolicy.PUBLISHED_ONLY, Instant.parse("2026-10-19T18:30:00Z"),
                Instant.parse("2026-10-20T18:30:00Z"));
        verify(assignments).findAllByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAscIdAsc(
                Set.of(42L), LmsContentPolicy.PUBLISHED_ONLY, Instant.parse("2026-11-01T04:00:00Z"),
                Instant.parse("2026-11-02T05:00:00Z"));
    }

    @Test
    void aStudentGetsTheirEventsAndOnlyPublishedDeadlinesOfCoursesTheyMayUse() {
        when(events.studentScope(ACCOUNT)).thenReturn(studentScope);
        when(events.findVisible(studentScope, OCT_1, OCT_31)).thenReturn(List.of(holiday));
        when(studentCourses.getAccessibleCourses(ACCOUNT))
                .thenReturn(List.of(studentCourse(dataStructures), studentCourse(databases)));
        deadlinesStored(
                assignment(5, databases, "2026-10-12T05:00:00Z", LmsContentStatus.PUBLISHED),
                assignment(3, dataStructures, "2026-10-12T05:00:00Z", LmsContentStatus.PUBLISHED),
                assignment(4, dataStructures, "2026-11-12T05:00:00Z", LmsContentStatus.PUBLISHED),
                assignment(6, dataStructures, "2026-10-13T05:00:00Z", LmsContentStatus.DRAFT),
                assignment(7, dataStructures, "2026-10-14T05:00:00Z", LmsContentStatus.ARCHIVED),
                assignment(8, someoneElses, "2026-10-15T05:00:00Z", LmsContentStatus.PUBLISHED));

        MyCalendarResponse calendar = service.getMyCalendar(new CalendarViewer(ACCOUNT, true, false, false), OCT_1, OCT_31, KOLKATA);

        assertThat(calendar.academicEvents()).containsExactly(holiday);
        assertThat(calendar.studentAssignmentDeadlines()).extracting(StudentAssignmentDeadline::assignmentId)
                .containsExactly(3L, 5L);
        StudentAssignmentDeadline first = calendar.studentAssignmentDeadlines().get(0);
        assertThat(first.lmsCourseId()).isEqualTo(42L);
        assertThat(first.courseCode()).isEqualTo("CS301");
        assertThat(first.maxMarks()).isEqualByComparingTo("20.00");
        assertThat(calendar.facultyAssignmentDeadlines()).isEmpty();
        verify(events, never()).facultyScope(any());
        verify(events, never()).findAllVisible(any(), any());
        verifyNoInteractions(facultyCourses);
    }

    @Test
    void aFacultyMemberGetsDraftAndPublishedDeadlinesOfCoursesTheyTeachButNotArchivedOnes() {
        when(events.facultyScope(ACCOUNT)).thenReturn(facultyScope);
        when(events.findVisible(facultyScope, OCT_1, OCT_31)).thenReturn(List.of(holiday));
        // The faculty course list already leaves out archived LMS courses and courses taught by someone else.
        when(facultyCourses.getAccessibleCourses(ACCOUNT))
                .thenReturn(List.of(facultyCourse(dataStructures, "PUBLISHED"), facultyCourse(databases, "DRAFT")));
        deadlinesStored(
                assignment(3, dataStructures, "2026-10-12T05:00:00Z", LmsContentStatus.DRAFT),
                assignment(4, databases, "2026-10-13T05:00:00Z", LmsContentStatus.PUBLISHED),
                assignment(5, dataStructures, "2026-10-14T05:00:00Z", LmsContentStatus.ARCHIVED),
                assignment(6, someoneElses, "2026-10-15T05:00:00Z", LmsContentStatus.PUBLISHED),
                assignment(7, databases, "2026-12-01T05:00:00Z", LmsContentStatus.DRAFT));

        MyCalendarResponse calendar = service.getMyCalendar(new CalendarViewer(ACCOUNT, false, true, false), OCT_1, OCT_31, KOLKATA);

        assertThat(calendar.academicEvents()).containsExactly(holiday);
        assertThat(calendar.facultyAssignmentDeadlines()).extracting(FacultyAssignmentDeadline::assignmentId,
                FacultyAssignmentDeadline::status).containsExactly(
                        tuple(3L, "DRAFT"), tuple(4L, "PUBLISHED"));
        assertThat(calendar.studentAssignmentDeadlines()).isEmpty();
        verify(assignments).findAllByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAscIdAsc(
                eq(Set.of(42L, 43L)), eq(LmsContentPolicy.IN_USE), any(), any());
        verify(events, never()).studentScope(any());
        verifyNoInteractions(studentCourses);
    }

    @Test
    void anAdminSeesEveryVisibleEventAndNoDeadlines() {
        when(events.findAllVisible(OCT_1, OCT_31)).thenReturn(List.of(holiday));

        MyCalendarResponse calendar = service.getMyCalendar(new CalendarViewer(1L, false, false, true), OCT_1, OCT_31, KOLKATA);

        assertThat(calendar.academicEvents()).containsExactly(holiday);
        assertThat(calendar.studentAssignmentDeadlines()).isEmpty();
        assertThat(calendar.facultyAssignmentDeadlines()).isEmpty();
        verify(events, never()).studentScope(any());
        verify(events, never()).facultyScope(any());
        verifyNoInteractions(studentCourses, facultyCourses, assignments);
    }

    @Test
    void aStudentWhoAlsoTeachesGetsBothScopesAndBothDeadlineLists() {
        when(events.studentScope(ACCOUNT)).thenReturn(studentScope);
        when(events.facultyScope(ACCOUNT)).thenReturn(facultyScope);
        when(events.findVisible(studentScope.and(facultyScope), OCT_1, OCT_31)).thenReturn(List.of(holiday));
        when(studentCourses.getAccessibleCourses(ACCOUNT)).thenReturn(List.of(studentCourse(dataStructures)));
        when(facultyCourses.getAccessibleCourses(ACCOUNT)).thenReturn(List.of(facultyCourse(databases, "DRAFT")));
        deadlinesStored(
                assignment(3, dataStructures, "2026-10-12T05:00:00Z", LmsContentStatus.PUBLISHED),
                assignment(4, databases, "2026-10-13T05:00:00Z", LmsContentStatus.DRAFT));

        MyCalendarResponse calendar = service.getMyCalendar(new CalendarViewer(ACCOUNT, true, true, false), OCT_1, OCT_31, KOLKATA);

        assertThat(calendar.academicEvents()).containsExactly(holiday);
        assertThat(calendar.studentAssignmentDeadlines()).extracting(StudentAssignmentDeadline::assignmentId).containsExactly(3L);
        assertThat(calendar.facultyAssignmentDeadlines()).extracting(FacultyAssignmentDeadline::assignmentId).containsExactly(4L);
        assertThat(studentScope.and(facultyScope).semesterIds()).containsExactlyInAnyOrder(9L, 10L);
    }

    @Test
    void anAdminWhoIsAlsoAStudentSeesEveryEventAndTheirOwnDeadlines() {
        when(events.findAllVisible(OCT_1, OCT_31)).thenReturn(List.of(holiday));
        when(studentCourses.getAccessibleCourses(ACCOUNT)).thenReturn(List.of(studentCourse(dataStructures)));
        deadlinesStored(assignment(3, dataStructures, "2026-10-12T05:00:00Z", LmsContentStatus.PUBLISHED));

        MyCalendarResponse calendar = service.getMyCalendar(new CalendarViewer(ACCOUNT, true, false, true), OCT_1, OCT_31, KOLKATA);

        assertThat(calendar.academicEvents()).containsExactly(holiday);
        assertThat(calendar.studentAssignmentDeadlines()).hasSize(1);
        verify(events, never()).studentScope(any());
    }

    @Test
    void withoutCoursesNoDeadlineQueryIsSent() {
        when(events.studentScope(ACCOUNT)).thenReturn(AcademicCalendarScope.NONE);
        when(events.facultyScope(ACCOUNT)).thenReturn(AcademicCalendarScope.NONE);
        when(studentCourses.getAccessibleCourses(ACCOUNT)).thenReturn(List.of());
        when(facultyCourses.getAccessibleCourses(ACCOUNT)).thenReturn(List.of());

        MyCalendarResponse calendar = service.getMyCalendar(new CalendarViewer(ACCOUNT, true, true, false), OCT_1, OCT_31, KOLKATA);

        assertThat(calendar.studentAssignmentDeadlines()).isEmpty();
        assertThat(calendar.facultyAssignmentDeadlines()).isEmpty();
        verifyNoInteractions(assignments);
    }
}
