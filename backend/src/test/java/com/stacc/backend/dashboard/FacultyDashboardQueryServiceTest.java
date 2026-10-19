package com.stacc.backend.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceSession;
import com.stacc.backend.academic.attendance.AttendanceSessionRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseQueryService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttemptRepository;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The faculty dashboard with the real managed-course query, so teaching follows the Teaching page's rule; the other
 * repositories are stand-ins. Status, deadline, and latest-attempt filtering happen in the database, so here the test
 * checks which courses and offerings are asked about, and how the answers are put together.
 */
class FacultyDashboardQueryServiceTest {

    private static final long FACULTY = 20L;
    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");

    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsAssignmentRepository assignments = mock(LmsAssignmentRepository.class);
    private final LmsSubmissionAttemptRepository attempts = mock(LmsSubmissionAttemptRepository.class);
    private final AttendanceSessionRepository sessions = mock(AttendanceSessionRepository.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final FacultyDashboardQueryService service = new FacultyDashboardQueryService(
            new LmsFacultyManagedCourseQueryService(teachingAssignments, lmsCourses), assignments, attempts, sessions, records,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final FacultyProfile faculty = AttendanceFixtures.faculty(FACULTY, 7);
    private final CourseOffering published = AttendanceFixtures.offering(31, "CS301", "Data Structures");
    private final CourseOffering draft = AttendanceFixtures.offering(32, "CS302", "Databases");
    private final CourseOffering noLms = AttendanceFixtures.offering(33, "CS303", "Networks");
    private final CourseOffering archived = AttendanceFixtures.offering(34, "CS304", "Compilers");
    private final CourseOffering ended = AttendanceFixtures.offering(35, "CS305", "Graphics");
    private final LmsCourse publishedLms = lmsCourse(published, 41, "PUBLISHED");

    @BeforeEach
    void setUp() {
        TeachingAssignment inactive = AttendanceFixtures.teaching(faculty, ended);
        inactive.deactivate();
        when(teachingAssignments.findAllWithCourseDetailsByUserAccountId(FACULTY)).thenReturn(List.of(
                AttendanceFixtures.teaching(faculty, published), AttendanceFixtures.teaching(faculty, published),
                AttendanceFixtures.teaching(faculty, draft), AttendanceFixtures.teaching(faculty, noLms),
                AttendanceFixtures.teaching(faculty, archived), inactive));
        when(lmsCourses.findAllByCourseOfferingIdIn(anyCollection())).thenReturn(List.of(
                publishedLms, lmsCourse(draft, 42, "DRAFT"), lmsCourse(archived, 44, "ARCHIVED")));
        when(assignments.findByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqual(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(attempts.findLatestUngradedByLmsCourseIdIn(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(sessions.findOpenByCourseOfferingIdIn(any(), any())).thenReturn(new PageImpl<>(List.of()));
    }

    private static LmsCourse lmsCourse(CourseOffering offering, long id, String status) {
        LmsCourse lmsCourse = new LmsCourse(offering);
        ReflectionTestUtils.setField(lmsCourse, "id", id);
        if (status.equals("PUBLISHED")) {
            lmsCourse.publish();
        } else if (status.equals("ARCHIVED")) {
            lmsCourse.archive();
        }
        return lmsCourse;
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    @Test
    void everyOfferingTaughtNowIsCountedOnceWithItsLmsStateNoneOrArchivedIncluded() {
        FacultyDashboardResponse dashboard = service.getDashboard(FACULTY);

        assertEquals(4, dashboard.teachingCourseCount());
        assertEquals(List.of("CS301:PUBLISHED", "CS302:DRAFT", "CS303:null", "CS304:ARCHIVED"), dashboard.teachingCourses().stream()
                .map(course -> course.courseCode() + ":" + course.lmsCourseStatus()).toList());
        assertEquals(new FacultyDashboardResponse.TeachingCourse(33L, "CS303", "Networks", new BigDecimal("4.00"), 3, "BTECH",
                "2026-27", null, null), dashboard.teachingCourses().get(2));
    }

    @Test
    void assignmentsAndGradingAreAskedOnlyForTheOpenLmsCoursesAndAttendanceForEveryOffering() {
        service.getDashboard(FACULTY);

        verify(assignments).findByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqual(eq(Set.of(41L, 42L)), eq(LmsContentPolicy.IN_USE),
                eq(NOW), eq(PageRequest.of(0, 5, Sort.by("dueAt").ascending()
                        .and(Sort.by("id").ascending()))));
        verify(attempts).findLatestUngradedByLmsCourseIdIn(Set.of(41L, 42L), PageRequest.of(0, 5));
        verify(sessions).findOpenByCourseOfferingIdIn(Set.of(31L, 32L, 33L, 34L), PageRequest.of(0, 5));
        // No open session, so no record counts are asked for.
        verify(records, never()).countRecordsBySessionIdIn(anyCollection());
    }

    @Test
    void upcomingAssignmentsKeepTheirStatusAndTheFullCount() {
        LmsAssignment future = withId(new LmsAssignment(publishedLms, "Lab 1", "Plain.", NOW.plusSeconds(3600), new BigDecimal("20.00")), 70);
        when(assignments.findByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqual(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(future), PageRequest.of(0, 5), 6));

        FacultyDashboardResponse dashboard = service.getDashboard(FACULTY);

        assertEquals(6, dashboard.upcomingAssignmentCount());
        assertEquals(List.of(new FacultyDashboardResponse.UpcomingAssignment(70L, 41L, "CS301", "Data Structures", "Lab 1",
                NOW.plusSeconds(3600), new BigDecimal("20.00"), "DRAFT")), dashboard.upcomingAssignments());
    }

    @Test
    void theGradingQueueShowsEachWaitingLatestAttemptWithItsStudentLoginIdAndTheFullCount() {
        LmsAssignment assignment = withId(new LmsAssignment(publishedLms, "Lab 1", "Plain.", NOW.minusSeconds(3600), new BigDecimal("20.00")), 70);
        List<LmsSubmissionAttempt> waiting = IntStream.range(0, 5).mapToObj(index -> {
            CourseEnrollment enrollment = AttendanceFixtures.enrollment(published, 50 + index, 140 + index, "STU-TEST-" + index);
            LmsAssignmentSubmission submission = withId(new LmsAssignmentSubmission(assignment, enrollment), 90 + index);
            // Handed in half an hour after a deadline an hour ago: late.
            return withId(new LmsSubmissionAttempt(submission, submission.takeNextAttemptNumber(), "Answer",
                    NOW.minusSeconds(1800 - index), NOW.minusSeconds(3600)), 200 + index);
        }).toList();
        when(attempts.findLatestUngradedByLmsCourseIdIn(any(), any())).thenReturn(new PageImpl<>(waiting, PageRequest.of(0, 5), 7));

        FacultyDashboardResponse dashboard = service.getDashboard(FACULTY);

        assertEquals(7, dashboard.needsGradingCount());
        assertEquals(5, dashboard.needsGrading().size());
        FacultyDashboardResponse.PendingSubmission first = dashboard.needsGrading().getFirst();
        assertEquals(90L, first.submissionId());
        assertEquals(70L, first.assignmentId());
        assertEquals(41L, first.lmsCourseId());
        assertEquals("STU-TEST-0", first.studentLoginId());
        assertEquals("Lab 1", first.assignmentTitle());
        assertEquals(1, first.latestAttemptNumber());
        assertTrue(first.latestLate());
    }

    @Test
    void theGradingQueryCountsOnlyAnUngradedLatestAttemptOfAPublishedAssignment() throws NoSuchMethodException {
        Query query = LmsSubmissionAttemptRepository.class
                .getMethod("findLatestUngradedByLmsCourseIdIn", Collection.class, Pageable.class)
                .getAnnotation(Query.class);
        for (String jpql : new String[] {query.value(), query.countQuery()}) {
            String compact = jpql.replaceAll("\\s+", " ");
            assertTrue(compact.contains("attempt.attemptNumber = submission.latestAttemptNumber"), jpql);
            assertTrue(compact.contains("not exists (select grade.id from LmsSubmissionGrade grade where grade.attempt = attempt)"), jpql);
            assertTrue(compact.contains("assignment.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED"), jpql);
            assertTrue(compact.contains("assignment.lmsCourse.id in :lmsCourseIds"), jpql);
        }
        assertTrue(query.value().replaceAll("\\s+", " ").contains("order by attempt.submittedAt asc, submission.id asc"));
    }

    @Test
    void openAttendanceShowsRealRecordCountsEvenWithoutAnLmsCourse() {
        AttendanceSession latest = AttendanceFixtures.session(noLms, faculty, 8, "2026-10-18T04:30:00Z");
        AttendanceSession earlier = AttendanceFixtures.session(published, faculty, 6, "2026-10-17T04:30:00Z");
        when(sessions.findOpenByCourseOfferingIdIn(any(), any())).thenReturn(new PageImpl<>(List.of(latest, earlier), PageRequest.of(0, 5), 2));
        AttendanceRecordRepository.SessionRecordCounts counts = mock(AttendanceRecordRepository.SessionRecordCounts.class);
        when(counts.getSessionId()).thenReturn(8L);
        when(counts.getTotalRecords()).thenReturn(40L);
        when(counts.getUnmarkedRecords()).thenReturn(3L);
        AttendanceRecordRepository.SessionRecordCounts allMarked = mock(AttendanceRecordRepository.SessionRecordCounts.class);
        when(allMarked.getSessionId()).thenReturn(6L);
        when(allMarked.getTotalRecords()).thenReturn(12L);
        when(allMarked.getUnmarkedRecords()).thenReturn(0L);
        when(records.countRecordsBySessionIdIn(Set.of(8L, 6L))).thenReturn(List.of(counts, allMarked));

        FacultyDashboardResponse dashboard = service.getDashboard(FACULTY);

        assertEquals(2, dashboard.openAttendanceCount());
        assertEquals(List.of(
                new FacultyDashboardResponse.OpenAttendanceSession(8L, 33L, "CS303", "Networks", Instant.parse("2026-10-18T04:30:00Z"), "Topic 8", 40, 3),
                new FacultyDashboardResponse.OpenAttendanceSession(6L, 31L, "CS301", "Data Structures", Instant.parse("2026-10-17T04:30:00Z"), "Topic 6", 12, 0)),
                dashboard.openAttendanceSessions());
    }

    @Test
    void withoutCurrentTeachingEverySectionIsEmptyAndNothingElseIsAsked() {
        faculty.deactivate();

        FacultyDashboardResponse dashboard = service.getDashboard(FACULTY);

        assertEquals(new FacultyDashboardResponse(0, List.of(), 0, List.of(), 0, List.of(), 0, List.of()), dashboard);
        verifyNoInteractions(assignments, attempts, sessions, records);
    }

    @Test
    void onlyArchivedLmsCoursesMeanNoAssignmentOrGradingQueryButAttendanceStillCounts() {
        when(teachingAssignments.findAllWithCourseDetailsByUserAccountId(FACULTY)).thenReturn(List.of(
                AttendanceFixtures.teaching(faculty, archived), AttendanceFixtures.teaching(faculty, noLms)));

        FacultyDashboardResponse dashboard = service.getDashboard(FACULTY);

        assertEquals(2, dashboard.teachingCourseCount());
        verifyNoInteractions(assignments, attempts);
        verify(sessions).findOpenByCourseOfferingIdIn(Set.of(33L, 34L), PageRequest.of(0, 5));
    }
}
