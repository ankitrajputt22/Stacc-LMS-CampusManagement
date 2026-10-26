package com.stacc.backend.report;

import static com.stacc.backend.report.ReportTestRows.assignment;
import static com.stacc.backend.report.ReportTestRows.enrollment;
import static com.stacc.backend.report.ReportTestRows.record;
import static com.stacc.backend.report.ReportTestRows.result;
import static com.stacc.backend.report.ReportTestRows.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceSessionRepository;
import com.stacc.backend.academic.attendance.AttendanceSessionStatus;
import com.stacc.backend.academic.attendance.AttendanceStatus;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.result.CourseResultStatus;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseQueryService;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseResponse;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttemptRepository;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class FacultyReportQueryServiceTest {

    private final LmsFacultyManagedCourseQueryService taught = mock(LmsFacultyManagedCourseQueryService.class);
    private final FacultyTeachingAccessService access = mock(FacultyTeachingAccessService.class);
    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final CourseEnrollmentRepository enrollments = mock(CourseEnrollmentRepository.class);
    private final LmsAssignmentRepository assignments = mock(LmsAssignmentRepository.class);
    private final LmsAssignmentSubmissionRepository submissions = mock(LmsAssignmentSubmissionRepository.class);
    private final LmsSubmissionAttemptRepository attempts = mock(LmsSubmissionAttemptRepository.class);
    private final AttendanceSessionRepository sessions = mock(AttendanceSessionRepository.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final CourseResultRepository results = mock(CourseResultRepository.class);
    private final FacultyReportQueryService service = new FacultyReportQueryService(taught, access, offerings, lmsCourses,
            enrollments, assignments, submissions, attempts, sessions, records, results);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static CourseOffering offering(long id) {
        Department cse = withId(new Department("CSE", "Computer Science"), 3);
        Program program = withId(new Program(cse, "BTECH-CSE", "B.Tech CSE", 8), 4);
        AcademicSession session = withId(new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)), 2);
        Semester semester = withId(new Semester(program, session, 3), 5);
        Course course = withId(new Course(cse, "CS301", "Data Structures", new BigDecimal("4.00")), 6);
        return withId(new CourseOffering(course, semester), id);
    }

    private static LmsCourse lmsCourse(long id, LmsCourseStatus status) {
        LmsCourse course = mock(LmsCourse.class);
        when(course.getId()).thenReturn(id);
        when(course.getStatus()).thenReturn(status);
        return course;
    }

    private void teaches(long offeringId) {
        when(access.canTeachCourseOffering(80L, offeringId)).thenReturn(true);
        when(offerings.findWithContextById(offeringId)).thenReturn(Optional.of(offering(offeringId)));
    }

    @Test
    void anOfferingTheFacultyMemberDoesNotActivelyTeachIsTheSame404AsAMissingOne() {
        when(access.canTeachCourseOffering(80L, 31L)).thenReturn(false);

        for (Long id : new Long[] {31L, 999L, null}) {
            assertThatThrownBy(() -> service.courseReport(80L, id)).isInstanceOfSatisfying(ApiException.class, refusal -> {
                assertThat(refusal.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(refusal.getMessage()).isEqualTo(FacultyReportQueryService.NOT_FOUND);
            });
        }
        verifyNoInteractions(offerings, enrollments, assignments, sessions, records, results, lmsCourses);
    }

    @Test
    void theCourseReportCountsEveryStatusExactly() {
        teaches(31L);
        when(enrollments.countByOfferingAndStatus(Set.of(31L))).thenReturn(List.of(
                enrollment(31, CourseEnrollmentStatus.ENROLLED, 2), enrollment(31, CourseEnrollmentStatus.COMPLETED, 1),
                enrollment(31, CourseEnrollmentStatus.WITHDRAWN, 1)));
        LmsCourse lms = lmsCourse(21, LmsCourseStatus.PUBLISHED);
        when(lmsCourses.findByCourseOfferingId(31L)).thenReturn(Optional.of(lms));
        when(assignments.countByCourseAndStatus(Set.of(21L), LmsContentPolicy.IN_USE)).thenReturn(List.of(
                assignment(21, LmsContentStatus.DRAFT, 1), assignment(21, LmsContentStatus.PUBLISHED, 2)));
        when(submissions.countSubmittedForPublishedAssignments(21L)).thenReturn(3L);
        when(attempts.countLatestUngradedByLmsCourseIdIn(Set.of(21L))).thenReturn(1L);
        when(sessions.countByOfferingAndStatus(Set.of(31L))).thenReturn(List.of(
                session(31, AttendanceSessionStatus.OPEN, 1), session(31, AttendanceSessionStatus.FINALIZED, 2)));
        when(records.countFinalizedRecordsByStatus(31L)).thenReturn(List.of(record(AttendanceStatus.PRESENT, 8),
                record(AttendanceStatus.ABSENT, 2), record(AttendanceStatus.UNMARKED, 5)));
        when(results.countByOfferingAndStatus(Set.of(31L))).thenReturn(List.of(result(31, CourseResultStatus.DRAFT, 3),
                result(31, CourseResultStatus.PUBLISHED, 7)));

        var report = service.courseReport(80L, 31L);

        assertThat(report.course().courseCode()).isEqualTo("CS301");
        assertThat(report.course().programCode()).isEqualTo("BTECH-CSE");
        assertThat(report.enrollment()).isEqualTo(new FacultyReports.FacultyEnrollmentReport(2, 1, 1));
        assertThat(report.assignments()).isEqualTo(new FacultyReports.FacultyAssignmentReport(true, 21L, "PUBLISHED",
                1, 2, 3, 1));
        // Unmarked records are reported as neither present nor absent.
        assertThat(report.attendance()).isEqualTo(new FacultyReports.FacultyAttendanceReport(1, 2, 8, 2));
        assertThat(report.results()).isEqualTo(new FacultyReports.FacultyResultReport(3, 7));
        // Archived assignments are never asked for.
        verify(assignments).countByCourseAndStatus(Set.of(21L), Set.of(LmsContentStatus.DRAFT, LmsContentStatus.PUBLISHED));
    }

    @Test
    void anOfferingWithoutAnLmsCourseStillGetsItsOfficialReport() {
        teaches(32L);
        when(lmsCourses.findByCourseOfferingId(32L)).thenReturn(Optional.empty());
        when(enrollments.countByOfferingAndStatus(Set.of(32L))).thenReturn(List.of(
                enrollment(32, CourseEnrollmentStatus.ENROLLED, 4)));
        when(results.countByOfferingAndStatus(Set.of(32L))).thenReturn(List.of(result(32, CourseResultStatus.PUBLISHED, 4)));

        var report = service.courseReport(80L, 32L);

        assertThat(report.assignments()).isEqualTo(new FacultyReports.FacultyAssignmentReport(false, null, null, 0, 0, 0, 0));
        assertThat(report.enrollment().enrolledCount()).isEqualTo(4);
        assertThat(report.results().publishedResultCount()).isEqualTo(4);
        assertThat(report.attendance()).isEqualTo(new FacultyReports.FacultyAttendanceReport(0, 0, 0, 0));
        verifyNoInteractions(assignments, submissions, attempts);
    }

    @Test
    void anArchivedLmsCourseIsNotCountedIn() {
        teaches(33L);
        LmsCourse archived = lmsCourse(23, LmsCourseStatus.ARCHIVED);
        when(lmsCourses.findByCourseOfferingId(33L)).thenReturn(Optional.of(archived));

        assertThat(service.courseReport(80L, 33L).assignments())
                .isEqualTo(new FacultyReports.FacultyAssignmentReport(false, 23L, "ARCHIVED", 0, 0, 0, 0));
        verifyNoInteractions(assignments, submissions, attempts);
    }

    @Test
    void myCoursesListsEveryTaughtOfferingWithGroupedCounts() {
        when(taught.getManagedCourses(80L)).thenReturn(List.of(
                new LmsFacultyManagedCourseResponse(31L, "CS301", "Data Structures", BigDecimal.ONE, 3, "BTECH", "2026-27", 21L, "PUBLISHED"),
                new LmsFacultyManagedCourseResponse(32L, "CS302", "Databases", BigDecimal.ONE, 3, "BTECH", "2026-27", null, null),
                new LmsFacultyManagedCourseResponse(33L, "CS303", "Operating Systems", BigDecimal.ONE, 3, "BTECH", "2026-27", 23L, "ARCHIVED")));
        when(enrollments.countByOfferingAndStatus(any())).thenReturn(List.of(
                enrollment(31, CourseEnrollmentStatus.ENROLLED, 2), enrollment(31, CourseEnrollmentStatus.WITHDRAWN, 1),
                enrollment(32, CourseEnrollmentStatus.ENROLLED, 5)));
        when(sessions.countByOfferingAndStatus(any())).thenReturn(List.of(session(31, AttendanceSessionStatus.FINALIZED, 2),
                session(31, AttendanceSessionStatus.OPEN, 1)));
        when(results.countByOfferingAndStatus(any())).thenReturn(List.of(result(32, CourseResultStatus.PUBLISHED, 5),
                result(32, CourseResultStatus.DRAFT, 1)));
        when(assignments.countByCourseAndStatus(any(), any())).thenReturn(List.of(assignment(21, LmsContentStatus.DRAFT, 1),
                assignment(21, LmsContentStatus.PUBLISHED, 2)));

        var list = service.myCourses(80L);

        assertThat(list).extracting(FacultyReports.FacultyReportCourseSummary::courseCode).containsExactly("CS301", "CS302", "CS303");
        assertThat(list.get(0)).satisfies(row -> {
            assertThat(row.activeCourseEnrollmentCount()).isEqualTo(2);
            assertThat(row.assignmentCount()).isEqualTo(3);
            assertThat(row.finalizedAttendanceSessionCount()).isEqualTo(2);
            assertThat(row.publishedResultCount()).isZero();
        });
        assertThat(list.get(1).assignmentCount()).isNull();
        assertThat(list.get(1).publishedResultCount()).isEqualTo(5);
        assertThat(list.get(2).assignmentCount()).isNull();
        // One grouped query per kind, over every offering at once; only the open LMS course is counted in.
        verify(enrollments).countByOfferingAndStatus(Set.of(31L, 32L, 33L));
        verify(assignments).countByCourseAndStatus(Set.of(21L), LmsContentPolicy.IN_USE);
    }

    @Test
    void noTeachingMeansNoQueriesAndAnEmptyList() {
        when(taught.getManagedCourses(80L)).thenReturn(List.of());
        assertThat(service.myCourses(80L)).isEmpty();
        verifyNoInteractions(enrollments, assignments, sessions, results);
    }

    @Test
    void withoutAnOpenLmsCourseNoAssignmentQueryIsSent() {
        when(taught.getManagedCourses(80L)).thenReturn(List.of(new LmsFacultyManagedCourseResponse(32L, "CS302",
                "Databases", BigDecimal.ONE, 3, "BTECH", "2026-27", null, null)));
        service.myCourses(80L);
        verify(assignments, never()).countByCourseAndStatus(any(), any());
        verify(access, never()).canTeachCourseOffering(anyLong(), eq(32L));
    }
}
