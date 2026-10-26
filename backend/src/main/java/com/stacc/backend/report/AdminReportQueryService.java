package com.stacc.backend.report;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.campus.services.CampusServiceRequestRepository;
import com.stacc.backend.campus.services.CampusServiceRequestStatus;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelAllocationStatus;
import com.stacc.backend.report.AdminReports.AdminAcademicReport;
import com.stacc.backend.report.AdminReports.AdminCampusServiceReport;
import com.stacc.backend.report.AdminReports.AdminCourseEnrollmentReportPage;
import com.stacc.backend.report.AdminReports.AdminCourseEnrollmentReportRow;
import com.stacc.backend.report.AdminReports.AdminEnrollmentSummary;
import com.stacc.backend.report.AdminReports.AdminHelpdeskReport;
import com.stacc.backend.report.AdminReports.AdminHostelReport;
import com.stacc.backend.report.AdminReports.AdminOperationalReport;
import com.stacc.backend.report.AdminReports.AdminReportFilters;
import com.stacc.backend.report.AdminReports.AdminReportOverview;
import com.stacc.backend.report.AdminReports.EnrollmentStatusCounts;
import com.stacc.backend.support.helpdesk.HelpdeskAccessService;
import com.stacc.backend.support.helpdesk.HelpdeskActor;
import com.stacc.backend.support.helpdesk.HelpdeskTicketRepository;
import com.stacc.backend.support.helpdesk.HelpdeskTicketStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin reports: academic and enrollment counts within optional session, program, and semester filters, one row per
 * course offering, and college-wide operational counts for the modules the admin may manage. Every count is a grouped
 * database query over the source tables; nothing is stored or cached. It reads only.
 */
@Service
@Transactional(readOnly = true)
public class AdminReportQueryService {

    public static final int MAX_PAGE_SIZE = 100;
    static final int CSV_BATCH = 500;
    static final List<String> CSV_HEADER = List.of("Academic Session", "Program", "Semester", "Course Code", "Course Name",
            "Enrolled", "Completed", "Withdrawn");

    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    private final CourseRepository courses;
    private final AcademicSessionRepository sessions;
    private final SemesterRepository semesters;
    private final CourseOfferingRepository offerings;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final CourseEnrollmentRepository courseEnrollments;
    private final HelpdeskAccessService helpdeskAccess;
    private final HelpdeskTicketRepository tickets;
    private final HostelAllocationRepository allocations;
    private final CampusServiceRequestRepository campusRequests;

    public AdminReportQueryService(DepartmentRepository departments, ProgramRepository programs, CourseRepository courses,
            AcademicSessionRepository sessions, SemesterRepository semesters, CourseOfferingRepository offerings,
            SemesterEnrollmentRepository semesterEnrollments, CourseEnrollmentRepository courseEnrollments,
            HelpdeskAccessService helpdeskAccess, HelpdeskTicketRepository tickets, HostelAllocationRepository allocations,
            CampusServiceRequestRepository campusRequests) {
        this.departments = departments;
        this.programs = programs;
        this.courses = courses;
        this.sessions = sessions;
        this.semesters = semesters;
        this.offerings = offerings;
        this.semesterEnrollments = semesterEnrollments;
        this.courseEnrollments = courseEnrollments;
        this.helpdeskAccess = helpdeskAccess;
        this.tickets = tickets;
        this.allocations = allocations;
        this.campusRequests = campusRequests;
    }

    /**
     * Checks the filters: each one given must exist (400 otherwise), and a semester must belong to the given program
     * and session, so a combination never quietly answers nothing.
     */
    public AdminReportFilters filters(Long academicSessionId, Long programId, Long semesterId) {
        if (academicSessionId != null && !sessions.existsById(academicSessionId)) {
            throw badRequest("The academic session filter does not match an academic session.");
        }
        if (programId != null && !programs.existsById(programId)) {
            throw badRequest("The program filter does not match a program.");
        }
        if (semesterId != null) {
            Semester semester = semesters.findById(semesterId)
                    .orElseThrow(() -> badRequest("The semester filter does not match a semester."));
            if (programId != null && !programId.equals(semester.getProgram().getId())) {
                throw badRequest("The semester does not belong to the selected program.");
            }
            if (academicSessionId != null && !academicSessionId.equals(semester.getAcademicSession().getId())) {
                throw badRequest("The semester does not belong to the selected academic session.");
            }
        }
        return new AdminReportFilters(academicSessionId, programId, semesterId);
    }

    public AdminReportOverview overview(ReportActor actor, AdminReportFilters filters) {
        AdminAcademicReport academic = new AdminAcademicReport(departments.count(), programs.count(), courses.count(),
                offerings.countWithin(filters.semesterId(), filters.programId(), filters.academicSessionId()));

        Map<SemesterEnrollmentStatus, Long> semesterCounts = new EnumMap<>(SemesterEnrollmentStatus.class);
        semesterEnrollments.countByStatusWithin(filters.semesterId(), filters.programId(), filters.academicSessionId())
                .forEach(row -> semesterCounts.merge(row.getStatus(), row.getTotal(), Long::sum));
        Map<CourseEnrollmentStatus, Long> courseCounts = new EnumMap<>(CourseEnrollmentStatus.class);
        courseEnrollments.countByStatusWithin(filters.semesterId(), filters.programId(), filters.academicSessionId())
                .forEach(row -> courseCounts.merge(row.getStatus(), row.getTotal(), Long::sum));
        AdminEnrollmentSummary enrollment = new AdminEnrollmentSummary(
                new EnrollmentStatusCounts(semesterCounts.getOrDefault(SemesterEnrollmentStatus.ENROLLED, 0L),
                        semesterCounts.getOrDefault(SemesterEnrollmentStatus.COMPLETED, 0L),
                        semesterCounts.getOrDefault(SemesterEnrollmentStatus.WITHDRAWN, 0L)),
                new EnrollmentStatusCounts(courseCounts.getOrDefault(CourseEnrollmentStatus.ENROLLED, 0L),
                        courseCounts.getOrDefault(CourseEnrollmentStatus.COMPLETED, 0L),
                        courseCounts.getOrDefault(CourseEnrollmentStatus.WITHDRAWN, 0L)));

        return new AdminReportOverview(filters, academic, enrollment,
                new AdminOperationalReport(helpdesk(actor), hostel(actor), campusServices(actor)));
    }

    /** One page of course offerings, in the admin offering list's order, each with its enrollments by status. */
    public AdminCourseEnrollmentReportPage enrollments(AdminReportFilters filters, int page, int size) {
        if (page < 0) {
            throw badRequest("The page must be 0 or more.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest("The page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        PageRequest request = PageRequest.of(page, size);
        long total = offerings.countWithin(filters.semesterId(), filters.programId(), filters.academicSessionId());
        List<CourseOffering> rows = offeringsOf(filters, request);
        List<AdminCourseEnrollmentReportRow> items = rowsOf(rows);
        return new AdminCourseEnrollmentReportPage(items, page, size, total, (int) Math.ceil((double) total / size));
    }

    /** Every row of the enrollment report, as CSV, under exactly the screen's filters. No IDs are written. */
    public String enrollmentCsv(AdminReportFilters filters) {
        List<CourseOffering> all = offeringsOf(filters, Pageable.unpaged());
        List<List<String>> lines = new ArrayList<>();
        for (int from = 0; from < all.size(); from += CSV_BATCH) {
            for (AdminCourseEnrollmentReportRow row : rowsOf(all.subList(from, Math.min(from + CSV_BATCH, all.size())))) {
                lines.add(List.of(row.academicSessionCode(), row.programCode(), String.valueOf(row.semesterNumber()),
                        row.courseCode(), row.courseName(), String.valueOf(row.enrolledCount()),
                        String.valueOf(row.completedCount()), String.valueOf(row.withdrawnCount())));
            }
        }
        return ReportCsv.write(CSV_HEADER, lines);
    }

    private List<CourseOffering> offeringsOf(AdminReportFilters filters, Pageable page) {
        // The admin offering list's own query and order: the latest session first, then program, semester, course.
        return offerings.findAllWithContext(filters.semesterId(), null, filters.academicSessionId(), filters.programId(),
                page);
    }

    /** The rows of these offerings, with every count in one grouped query. */
    private List<AdminCourseEnrollmentReportRow> rowsOf(List<CourseOffering> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<CourseEnrollmentStatus, Long>> counts = courseEnrollments.countByOfferingAndStatus(
                rows.stream().map(CourseOffering::getId).toList()).stream()
                .collect(Collectors.groupingBy(CourseEnrollmentRepository.OfferingStatusCount::getCourseOfferingId,
                        Collectors.toMap(CourseEnrollmentRepository.OfferingStatusCount::getStatus,
                                CourseEnrollmentRepository.OfferingStatusCount::getTotal, Long::sum)));
        return rows.stream().map(offering -> {
            Map<CourseEnrollmentStatus, Long> byStatus = counts.getOrDefault(offering.getId(), Map.of());
            Semester semester = offering.getSemester();
            return new AdminCourseEnrollmentReportRow(offering.getId(), offering.getCourse().getCode(),
                    offering.getCourse().getName(), semester.getProgram().getCode(), semester.getSemesterNumber(),
                    semester.getAcademicSession().getCode(), byStatus.getOrDefault(CourseEnrollmentStatus.ENROLLED, 0L),
                    byStatus.getOrDefault(CourseEnrollmentStatus.COMPLETED, 0L),
                    byStatus.getOrDefault(CourseEnrollmentStatus.WITHDRAWN, 0L));
        }).toList();
    }

    /** Only for an admin who may see every ticket, under the helpdesk's own rule. */
    private AdminHelpdeskReport helpdesk(ReportActor actor) {
        if (!helpdeskAccess.canViewAll(HelpdeskActor.of(actor.accountId(), actor.grantedAuthorities()))) {
            return null;
        }
        Map<HelpdeskTicketStatus, Long> byStatus = tickets.countByStatus().stream()
                .collect(Collectors.toMap(HelpdeskTicketRepository.StatusCount::getStatus,
                        HelpdeskTicketRepository.StatusCount::getTotal, Long::sum));
        return new AdminHelpdeskReport(byStatus.getOrDefault(HelpdeskTicketStatus.OPEN, 0L),
                byStatus.getOrDefault(HelpdeskTicketStatus.IN_PROGRESS, 0L),
                byStatus.getOrDefault(HelpdeskTicketStatus.RESOLVED, 0L),
                byStatus.getOrDefault(HelpdeskTicketStatus.CLOSED, 0L));
    }

    private AdminHostelReport hostel(ReportActor actor) {
        if (!actor.holdsWith("ADMIN", "HOSTEL_MANAGE")) {
            return null;
        }
        Map<HostelAllocationStatus, Long> byStatus = allocations.countByStatus().stream()
                .collect(Collectors.toMap(HostelAllocationRepository.StatusCount::getStatus,
                        HostelAllocationRepository.StatusCount::getTotal, Long::sum));
        return new AdminHostelReport(byStatus.getOrDefault(HostelAllocationStatus.ACTIVE, 0L),
                byStatus.getOrDefault(HostelAllocationStatus.VACATED, 0L));
    }

    /** Counted by the status a request shows: a SUBMITTED one whose approval was decided counts as that decision. */
    private AdminCampusServiceReport campusServices(ReportActor actor) {
        if (!actor.holdsWith("ADMIN", "CAMPUS_SERVICE_MANAGE")) {
            return null;
        }
        Map<CampusServiceRequestStatus, Long> byStatus = new EnumMap<>(CampusServiceRequestStatus.class);
        campusRequests.countByStatusAndApprovalStatus().forEach(row -> byStatus.merge(
                row.getStatus().withApproval(row.getApprovalStatus()), row.getTotal(), Long::sum));
        return new AdminCampusServiceReport(byStatus.getOrDefault(CampusServiceRequestStatus.SUBMITTED, 0L),
                byStatus.getOrDefault(CampusServiceRequestStatus.APPROVED, 0L),
                byStatus.getOrDefault(CampusServiceRequestStatus.REJECTED, 0L),
                byStatus.getOrDefault(CampusServiceRequestStatus.CANCELLED, 0L),
                byStatus.getOrDefault(CampusServiceRequestStatus.COMPLETED, 0L));
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
