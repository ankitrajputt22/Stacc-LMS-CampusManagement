package com.stacc.backend.report;

import static com.stacc.backend.report.ReportTestRows.allocation;
import static com.stacc.backend.report.ReportTestRows.courseEnrollment;
import static com.stacc.backend.report.ReportTestRows.enrollment;
import static com.stacc.backend.report.ReportTestRows.request;
import static com.stacc.backend.report.ReportTestRows.semesterEnrollment;
import static com.stacc.backend.report.ReportTestRows.ticket;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.campus.services.CampusServiceRequestRepository;
import com.stacc.backend.campus.services.CampusServiceRequestStatus;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelAllocationStatus;
import com.stacc.backend.report.AdminReports.AdminReportFilters;
import com.stacc.backend.support.helpdesk.HelpdeskAccessService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketRepository;
import com.stacc.backend.support.helpdesk.HelpdeskTicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class AdminReportQueryServiceTest {

    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final SemesterEnrollmentRepository semesterEnrollments = mock(SemesterEnrollmentRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final HelpdeskTicketRepository tickets = mock(HelpdeskTicketRepository.class);
    private final HostelAllocationRepository allocations = mock(HostelAllocationRepository.class);
    private final CampusServiceRequestRepository campusRequests = mock(CampusServiceRequestRepository.class);
    private final AdminReportQueryService service = new AdminReportQueryService(departments, programs, courses, sessions,
            semesters, offerings, semesterEnrollments, courseEnrollments, new HelpdeskAccessService(), tickets, allocations,
            campusRequests);

    private final AdminReportFilters none = new AdminReportFilters(null, null, null);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static ReportActor admin(String... permissions) {
        return new ReportActor(1L, Set.of("ADMIN"), Set.of(permissions));
    }

    private static Semester semester(long id, long programId, long sessionId, String code, int number) {
        Department cse = withId(new Department("CSE", "Computer Science"), 3);
        Program program = withId(new Program(cse, "BTECH-CSE", "B.Tech CSE", 8), programId);
        AcademicSession session = withId(new AcademicSession(code, LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)), sessionId);
        return withId(new Semester(program, session, number), id);
    }

    private static CourseOffering offering(long id, String code, String name, Semester semester) {
        Course course = withId(new Course(semester.getProgram().getDepartment(), code, name, new BigDecimal("4.00")), id + 100);
        return withId(new CourseOffering(course, semester), id);
    }

    private static void assertBadRequest(Runnable call, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    @Test
    void filtersMustExistAndASemesterMustBelongToTheProgramAndSession() {
        when(sessions.existsById(2L)).thenReturn(true);
        when(programs.existsById(4L)).thenReturn(true);
        when(programs.existsById(9L)).thenReturn(true);
        when(sessions.existsById(8L)).thenReturn(true);
        when(semesters.findById(5L)).thenReturn(Optional.of(semester(5, 4, 2, "2026-27", 3)));

        assertThat(service.filters(2L, 4L, 5L)).isEqualTo(new AdminReportFilters(2L, 4L, 5L));
        assertThat(service.filters(null, null, null)).isEqualTo(none);
        assertBadRequest(() -> service.filters(99L, null, null), "The academic session filter does not match an academic session.");
        assertBadRequest(() -> service.filters(null, 99L, null), "The program filter does not match a program.");
        assertBadRequest(() -> service.filters(null, null, 99L), "The semester filter does not match a semester.");
        assertBadRequest(() -> service.filters(null, 9L, 5L), "The semester does not belong to the selected program.");
        assertBadRequest(() -> service.filters(8L, null, 5L), "The semester does not belong to the selected academic session.");
    }

    @Test
    void theOverviewCountsEnrollmentsByExactStatusWithinTheFilters() {
        when(departments.count()).thenReturn(2L);
        when(programs.count()).thenReturn(3L);
        when(courses.count()).thenReturn(7L);
        when(offerings.countWithin(null, 4L, 2L)).thenReturn(5L);
        when(semesterEnrollments.countByStatusWithin(null, 4L, 2L)).thenReturn(List.of(
                semesterEnrollment(SemesterEnrollmentStatus.ENROLLED, 3), semesterEnrollment(SemesterEnrollmentStatus.WITHDRAWN, 1)));
        when(courseEnrollments.countByStatusWithin(null, 4L, 2L)).thenReturn(List.of(
                courseEnrollment(CourseEnrollmentStatus.ENROLLED, 9), courseEnrollment(CourseEnrollmentStatus.COMPLETED, 4),
                courseEnrollment(CourseEnrollmentStatus.WITHDRAWN, 2)));

        var overview = service.overview(admin("ADMIN_REPORT_VIEW"), new AdminReportFilters(2L, 4L, null));

        assertThat(overview.academic()).isEqualTo(new AdminReports.AdminAcademicReport(2, 3, 7, 5));
        assertThat(overview.enrollment().semesterEnrollments()).isEqualTo(new AdminReports.EnrollmentStatusCounts(3, 0, 1));
        assertThat(overview.enrollment().courseEnrollments()).isEqualTo(new AdminReports.EnrollmentStatusCounts(9, 4, 2));
        assertThat(overview.filters()).isEqualTo(new AdminReportFilters(2L, 4L, null));
    }

    @Test
    void operationalSectionsNeedEachModulesOwnPermission() {
        when(tickets.countByStatus()).thenReturn(List.of(ticket(HelpdeskTicketStatus.OPEN, 4),
                ticket(HelpdeskTicketStatus.IN_PROGRESS, 1), ticket(HelpdeskTicketStatus.CLOSED, 2)));
        when(allocations.countByStatus()).thenReturn(List.of(allocation(HostelAllocationStatus.ACTIVE, 6),
                allocation(HostelAllocationStatus.VACATED, 2)));
        when(campusRequests.countByStatusAndApprovalStatus()).thenReturn(List.of(
                request(CampusServiceRequestStatus.SUBMITTED, ApprovalRequestStatus.PENDING, 2),
                request(CampusServiceRequestStatus.SUBMITTED, ApprovalRequestStatus.APPROVED, 1),
                request(CampusServiceRequestStatus.SUBMITTED, ApprovalRequestStatus.REJECTED, 1),
                request(CampusServiceRequestStatus.APPROVED, null, 3),
                request(CampusServiceRequestStatus.COMPLETED, ApprovalRequestStatus.APPROVED, 1),
                request(CampusServiceRequestStatus.CANCELLED, ApprovalRequestStatus.CANCELLED, 1)));

        var all = service.overview(admin("ADMIN_REPORT_VIEW", "HELPDESK_TICKET_VIEW_ALL", "HOSTEL_MANAGE",
                "CAMPUS_SERVICE_MANAGE"), none).operations();
        assertThat(all.helpdesk()).isEqualTo(new AdminReports.AdminHelpdeskReport(4, 1, 0, 2));
        assertThat(all.hostel()).isEqualTo(new AdminReports.AdminHostelReport(6, 2));
        // A submitted request whose approval was decided counts as that decision.
        assertThat(all.campusServices()).isEqualTo(new AdminReports.AdminCampusServiceReport(2, 4, 1, 1, 1));

        var none = service.overview(admin("ADMIN_REPORT_VIEW"), this.none).operations();
        assertThat(none.helpdesk()).isNull();
        assertThat(none.hostel()).isNull();
        assertThat(none.campusServices()).isNull();

        // The permission without the ADMIN role is no pass either.
        var noRole = service.overview(new ReportActor(1L, Set.of("FACULTY"), Set.of("HELPDESK_TICKET_VIEW_ALL",
                "HOSTEL_MANAGE", "CAMPUS_SERVICE_MANAGE")), this.none).operations();
        assertThat(noRole.helpdesk()).isNull();
        assertThat(noRole.hostel()).isNull();
        assertThat(noRole.campusServices()).isNull();
    }

    @Test
    void withoutThePermissionTheModuleIsNeverQueried() {
        service.overview(admin("ADMIN_REPORT_VIEW"), none);
        verifyNoInteractions(tickets, allocations, campusRequests);
    }

    @Test
    void theEnrollmentReportIsOnePagePerOfferingWithOneGroupedCount() {
        Semester third = semester(5, 4, 2, "2026-27", 3);
        when(offerings.countWithin(null, null, 2L)).thenReturn(3L);
        when(offerings.findAllWithContext(isNull(), isNull(), eq(2L), isNull(), any(Pageable.class))).thenReturn(List.of(
                offering(31, "CS301", "Data Structures", third), offering(32, "CS302", "Databases", third)));
        when(courseEnrollments.countByOfferingAndStatus(List.of(31L, 32L))).thenReturn(List.of(
                enrollment(31, CourseEnrollmentStatus.ENROLLED, 2), enrollment(31, CourseEnrollmentStatus.COMPLETED, 1),
                enrollment(31, CourseEnrollmentStatus.WITHDRAWN, 1)));

        var page = service.enrollments(new AdminReportFilters(2L, null, null), 0, 2);

        assertThat(page.items()).extracting(AdminReports.AdminCourseEnrollmentReportRow::courseCode).containsExactly("CS301", "CS302");
        assertThat(page.items().get(0)).isEqualTo(new AdminReports.AdminCourseEnrollmentReportRow(31L, "CS301",
                "Data Structures", "BTECH-CSE", 3, "2026-27", 2, 1, 1));
        assertThat(page.items().get(1).enrolledCount()).isZero();
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        verify(offerings).findAllWithContext(null, null, 2L, null, PageRequest.of(0, 2));
    }

    @Test
    void anEmptyPageSendsNoCountQueryAndPagesAreBounded() {
        when(offerings.findAllWithContext(any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of());
        assertThat(service.enrollments(none, 0, 20).items()).isEmpty();
        verify(courseEnrollments, never()).countByOfferingAndStatus(any());
        assertBadRequest(() -> service.enrollments(none, -1, 20), "The page must be 0 or more.");
        assertBadRequest(() -> service.enrollments(none, 0, 0), "The page size must be between 1 and 100.");
        assertBadRequest(() -> service.enrollments(none, 0, 101), "The page size must be between 1 and 100.");
    }

    @Test
    void theCsvUsesTheSameFiltersAndWritesNoIds() {
        Semester third = semester(5, 4, 2, "2026-27", 3);
        when(offerings.findAllWithContext(isNull(), isNull(), eq(2L), isNull(), eq(Pageable.unpaged()))).thenReturn(List.of(
                offering(31, "CS301", "=cmd|' /C calc'!A0", third)));
        when(courseEnrollments.countByOfferingAndStatus(List.of(31L))).thenReturn(List.of(
                enrollment(31, CourseEnrollmentStatus.ENROLLED, 2)));

        String csv = service.enrollmentCsv(new AdminReportFilters(2L, null, null));

        assertThat(csv).isEqualTo("\"Academic Session\",\"Program\",\"Semester\",\"Course Code\",\"Course Name\","
                + "\"Enrolled\",\"Completed\",\"Withdrawn\"\r\n"
                + "\"2026-27\",\"BTECH-CSE\",\"3\",\"CS301\",\"'=cmd|' /C calc'!A0\",\"2\",\"0\",\"0\"\r\n");
        assertThat(csv).doesNotContain("31");
    }
}
