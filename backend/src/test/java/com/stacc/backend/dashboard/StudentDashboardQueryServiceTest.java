package com.stacc.backend.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.attendance.AttendanceRecord;
import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceSession;
import com.stacc.backend.academic.attendance.AttendanceStatus;
import com.stacc.backend.academic.attendance.api.StudentAttendanceQueryService;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.result.CourseResult;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.result.api.StudentResultQueryService;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.announcement.LmsAnnouncement;
import com.stacc.backend.lms.announcement.LmsAnnouncementRepository;
import com.stacc.backend.lms.api.LmsStudentCourseQueryService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.membership.LmsStudentMembership;
import com.stacc.backend.lms.membership.LmsStudentMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The dashboard with the real student course, attendance, and result query services; only the repositories are
 * stand-ins. Status and deadline filtering of assignments and announcements happens in the database, so here the
 * test checks which courses, status, moment, and page are asked for.
 */
class StudentDashboardQueryServiceTest {

    private static final long STUDENT = 50L;
    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");

    private final LmsStudentMembershipRepository memberships = mock(LmsStudentMembershipRepository.class);
    private final LmsAssignmentRepository assignments = mock(LmsAssignmentRepository.class);
    private final LmsAnnouncementRepository announcements = mock(LmsAnnouncementRepository.class);
    private final AttendanceRecordRepository attendanceRecords = mock(AttendanceRecordRepository.class);
    private final CourseResultRepository results = mock(CourseResultRepository.class);
    private final StudentDashboardQueryService service = new StudentDashboardQueryService(
            new LmsStudentCourseQueryService(memberships, mock(LmsCourseRepository.class), mock(LmsStudentAccessService.class)),
            assignments, announcements, new StudentAttendanceQueryService(attendanceRecords),
            new StudentResultQueryService(results), Clock.fixed(NOW, ZoneOffset.UTC));

    private final FacultyProfile faculty = AttendanceFixtures.faculty(20, 7);
    private final CourseOffering offeringA = AttendanceFixtures.offering(31, "CS301", "Data Structures");
    private final CourseOffering offeringB = AttendanceFixtures.offering(32, "CS302", "Databases");
    private final CourseOffering offeringC = AttendanceFixtures.offering(33, "CS303", "Networks");
    private final CourseOffering offeringD = AttendanceFixtures.offering(34, "CS304", "Compilers");
    private final LmsCourse published = lmsCourse(offeringA, 41, "PUBLISHED");
    private final CourseEnrollment enrollmentA = AttendanceFixtures.enrollment(offeringA, STUDENT, 140, "STU-TEST-A");

    @BeforeEach
    void setUp() {
        CourseEnrollment enrollmentD = AttendanceFixtures.enrollment(offeringD, STUDENT, 143, "STU-TEST-A");
        enrollmentD.withdraw();
        when(memberships.findAllWithCourseDetailsByUserAccountId(STUDENT)).thenReturn(List.of(
                new LmsStudentMembership(enrollmentA, published),
                new LmsStudentMembership(AttendanceFixtures.enrollment(offeringB, STUDENT, 141, "STU-TEST-A"), lmsCourse(offeringB, 42, "DRAFT")),
                new LmsStudentMembership(AttendanceFixtures.enrollment(offeringC, STUDENT, 142, "STU-TEST-A"), lmsCourse(offeringC, 43, "ARCHIVED")),
                new LmsStudentMembership(enrollmentD, lmsCourse(offeringD, 44, "PUBLISHED"))));
        when(assignments.findByLmsCourseIdInAndStatusAndDueAtGreaterThanEqual(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
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

    @SuppressWarnings("unchecked")
    private Collection<Long> askedCourseIds() {
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(assignments).findByLmsCourseIdInAndStatusAndDueAtGreaterThanEqual(ids.capture(), any(), any(), any());
        return ids.getValue();
    }

    @Test
    void onlyTheCoursesTheStudentCanUseNowAreShownAndCounted() {
        StudentDashboardResponse dashboard = service.getDashboard(STUDENT);

        assertEquals(1, dashboard.courseCount());
        assertEquals(List.of(new StudentDashboardResponse.Course(41L, "CS301", "Data Structures", new BigDecimal("4.00"), 3,
                "BTECH", "2026-27")), dashboard.courses());
    }

    @Test
    void upcomingAssignmentsArePublishedAndDueFromNowInTheCurrentCoursesOnlyNearestFirst() {
        List<LmsAssignment> page = IntStream.range(0, 5)
                .mapToObj(index -> withId(new LmsAssignment(published, "Task " + index, "Plain text.",
                        NOW.plusSeconds(3600L * (index + 1)), new BigDecimal("20.00")), 70 + index))
                .toList();
        page.forEach(assignment -> assignment.publish(NOW.minusSeconds(60)));
        when(assignments.findByLmsCourseIdInAndStatusAndDueAtGreaterThanEqual(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(page, PageRequest.of(0, 5), 8));

        StudentDashboardResponse dashboard = service.getDashboard(STUDENT);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(assignments).findByLmsCourseIdInAndStatusAndDueAtGreaterThanEqual(
                eq(Set.of(41L)), eq(LmsContentStatus.PUBLISHED),
                eq(NOW), pageable.capture());
        assertEquals(PageRequest.of(0, 5, Sort.by("dueAt").ascending().and(Sort.by("id").ascending())), pageable.getValue());
        assertEquals(8, dashboard.upcomingAssignmentCount());
        assertEquals(5, dashboard.upcomingAssignments().size());
        assertEquals(new StudentDashboardResponse.UpcomingAssignment(70L, 41L, "CS301", "Data Structures", "Task 0",
                NOW.plusSeconds(3600), new BigDecimal("20.00")), dashboard.upcomingAssignments().getFirst());
    }

    @Test
    void recentAnnouncementsArePublishedOnesOfTheCurrentCoursesNewestFirst() {
        LmsAnnouncement announcement = withId(new LmsAnnouncement(published, "Lab moved", "Thursday."), 11);
        announcement.publish(NOW.minusSeconds(3600));
        when(announcements.findByLmsCourseIdInAndStatus(any(), any(), any())).thenReturn(List.of(announcement));

        StudentDashboardResponse dashboard = service.getDashboard(STUDENT);

        verify(announcements).findByLmsCourseIdInAndStatus(Set.of(41L), LmsContentStatus.PUBLISHED,
                PageRequest.of(0, 5, Sort.by("publishedAt").descending().and(Sort.by("id").descending())));
        assertEquals(List.of(new StudentDashboardResponse.RecentAnnouncement(11L, 41L, "CS301", "Data Structures", "Lab moved",
                NOW.minusSeconds(3600))), dashboard.recentAnnouncements());
    }

    @Test
    void withoutCurrentCoursesNoLmsQueryIsMadeButAttendanceAndResultsStillShow() {
        when(memberships.findAllWithCourseDetailsByUserAccountId(STUDENT)).thenReturn(List.of());
        enrollmentA.markCompleted();
        when(attendanceRecords.findAllFinalizedByUserAccountId(STUDENT)).thenReturn(List.of(record(offeringA, 1, true, AttendanceStatus.PRESENT)));
        when(results.findAllPublishedByUserAccountId(STUDENT)).thenReturn(List.of(result(offeringA, "A", NOW.minusSeconds(60), true)));

        StudentDashboardResponse dashboard = service.getDashboard(STUDENT);

        assertEquals(0, dashboard.courseCount());
        assertTrue(dashboard.courses().isEmpty() && dashboard.upcomingAssignments().isEmpty() && dashboard.recentAnnouncements().isEmpty());
        assertEquals(0, dashboard.upcomingAssignmentCount());
        assertEquals(1, dashboard.attendance().size());
        assertEquals(1, dashboard.recentResults().size());
        verifyNoInteractions(assignments, announcements);
    }

    private AttendanceRecord record(CourseOffering offering, long id, boolean finalized, AttendanceStatus status) {
        AttendanceSession session = AttendanceFixtures.session(offering, faculty, id, "2026-10-01T04:30:00Z");
        AttendanceRecord record = AttendanceFixtures.record(session, AttendanceFixtures.enrollment(offering, STUDENT, 140, "STU-TEST-A"), id);
        record.mark(status, faculty, NOW.minusSeconds(7200));
        if (finalized) {
            session.markFinalized(NOW.minusSeconds(3600));
        }
        return record;
    }

    @Test
    void attendanceFollowsTheAttendancePageFinalizedOnlyPerCourseAndNeverOverall() {
        List<AttendanceRecord> stored = new ArrayList<>(List.of(
                record(offeringA, 1, true, AttendanceStatus.PRESENT),
                record(offeringA, 2, true, AttendanceStatus.ABSENT),
                record(offeringB, 3, false, AttendanceStatus.PRESENT)));
        for (int index = 0; index < 4; index++) {
            stored.add(record(AttendanceFixtures.offering(50 + index, "CS40" + index, "Course " + index), 10 + index, true, AttendanceStatus.PRESENT));
        }
        when(attendanceRecords.findAllFinalizedByUserAccountId(STUDENT)).thenReturn(stored);

        StudentDashboardResponse dashboard = service.getDashboard(STUDENT);

        assertEquals(5, dashboard.attendanceCourseCount());
        assertEquals(4, dashboard.attendance().size());
        assertEquals(new StudentDashboardResponse.AttendanceCourse(31L, "CS301", "Data Structures", 3, "BTECH", "2026-27", 2, 1, 1,
                new BigDecimal("50.00")), dashboard.attendance().getFirst());
        assertTrue(dashboard.attendance().stream().noneMatch(course -> course.courseCode().equals("CS302")));
        List<String> fields = Arrays.stream(StudentDashboardResponse.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertEquals(List.of("courseCount", "courses", "upcomingAssignmentCount", "upcomingAssignments", "recentAnnouncements",
                "attendanceCourseCount", "attendance", "resultCount", "recentResults"), fields);
    }

    private CourseResult result(CourseOffering offering, String grade, Instant publishedAt, boolean publish) {
        CourseResult result = new CourseResult(AttendanceFixtures.enrollment(offering, STUDENT, 140, "STU-TEST-A"), faculty,
                new BigDecimal("82.00"), new BigDecimal("100.00"), grade, "Remark");
        if (publish) {
            result.publish(publishedAt);
        }
        return result;
    }

    @Test
    void recentResultsArePublishedOwnResultsNewestFirstWithoutRemarks() {
        List<CourseResult> stored = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            stored.add(result(AttendanceFixtures.offering(60 + index, "CS50" + index, "Course " + index), index == 2 ? null : "B",
                    NOW.minusSeconds(86400L * (6 - index)), true));
        }
        stored.add(result(offeringD, "A", null, false));
        when(results.findAllPublishedByUserAccountId(STUDENT)).thenReturn(stored);

        StudentDashboardResponse dashboard = service.getDashboard(STUDENT);

        assertEquals(6, dashboard.resultCount());
        assertEquals(List.of("CS505", "CS504", "CS503", "CS502", "CS501"),
                dashboard.recentResults().stream().map(StudentDashboardResponse.RecentResult::courseCode).toList());
        assertNull(dashboard.recentResults().get(3).gradeCode());
        assertTrue(Arrays.stream(StudentDashboardResponse.RecentResult.class.getRecordComponents())
                .noneMatch(component -> component.getName().equals("remarks")));
    }

    @Test
    void everythingIsReadForTheSignedInAccountOnly() {
        service.getDashboard(STUDENT);

        verify(memberships).findAllWithCourseDetailsByUserAccountId(STUDENT);
        verify(attendanceRecords).findAllFinalizedByUserAccountId(STUDENT);
        verify(results).findAllPublishedByUserAccountId(STUDENT);
        verify(memberships, never()).findAllWithCourseDetailsByUserAccountId(longThat(id -> id != STUDENT));
        assertEquals(Set.of(41L), Set.copyOf(askedCourseIds()));
        // Another student's membership in the same published course does not count for this one.
        CourseEnrollment someoneElse = AttendanceFixtures.enrollment(offeringA, 51, 150, "STU-TEST-B");
        when(memberships.findAllWithCourseDetailsByUserAccountId(STUDENT)).thenReturn(List.of(new LmsStudentMembership(someoneElse, published)));
        assertEquals(0, service.getDashboard(STUDENT).courseCount());
        verify(attendanceRecords, never()).findAllFinalizedByUserAccountId(51L);
    }
}
