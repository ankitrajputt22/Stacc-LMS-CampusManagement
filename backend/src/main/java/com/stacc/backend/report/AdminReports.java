package com.stacc.backend.report;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Admin reports as clients see them: counts only. Operational sections are null when the admin lacks that module's
 * own permission, so a missing section is never shown as a zero.
 */
public final class AdminReports {

    private AdminReports() {
    }

    /** The filters that were applied, as given; each one is optional. */
    public record AdminReportFilters(
            @Schema(nullable = true) Long academicSessionId,
            @Schema(nullable = true) Long programId,
            @Schema(nullable = true) Long semesterId) {
    }

    /**
     * Departments, programs, and catalog courses are counted college-wide, whatever the filters: they do not belong to
     * a session or semester. Course offerings are counted within the filters.
     */
    public record AdminAcademicReport(
            @Schema(description = "Every department, any status; not filtered.") long departmentCount,
            @Schema(description = "Every program, any status; not filtered.") long programCount,
            @Schema(description = "Every catalog course, any status; not filtered.") long courseCount,
            @Schema(description = "Course offerings in the filtered semesters, any status.") long courseOfferingCount) {
    }

    /** Enrollments by their exact status. ENROLLED is current participation; nothing else is called active. */
    public record EnrollmentStatusCounts(long enrolledCount, long completedCount, long withdrawnCount) {
    }

    public record AdminHelpdeskReport(long openTicketCount, long inProgressTicketCount, long resolvedTicketCount,
            long closedTicketCount) {
    }

    public record AdminHostelReport(long activeAllocationCount, long vacatedAllocationCount) {
    }

    /** Requests by the status they show, with an approval's outcome taken into account. */
    public record AdminCampusServiceReport(long submittedCount, long approvedCount, long rejectedCount,
            long cancelledCount, long completedCount) {
    }

    /** College-wide operational counts; filters do not apply. Each section needs its module's own permission. */
    public record AdminOperationalReport(
            @Schema(nullable = true, description = "Only with ADMIN and HELPDESK_TICKET_VIEW_ALL.") AdminHelpdeskReport helpdesk,
            @Schema(nullable = true, description = "Only with ADMIN and HOSTEL_MANAGE.") AdminHostelReport hostel,
            @Schema(nullable = true, description = "Only with ADMIN and CAMPUS_SERVICE_MANAGE.") AdminCampusServiceReport campusServices) {
    }

    /** Semester and course enrollments in the filtered semesters. */
    public record AdminEnrollmentSummary(EnrollmentStatusCounts semesterEnrollments,
            EnrollmentStatusCounts courseEnrollments) {
    }

    public record AdminReportOverview(AdminReportFilters filters, AdminAcademicReport academic,
            AdminEnrollmentSummary enrollment, AdminOperationalReport operations) {
    }

    /** One course offering with its course enrollments by status. */
    public record AdminCourseEnrollmentReportRow(
            Long courseOfferingId,
            @Schema(example = "CS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "BTECH-CSE") String programCode,
            int semesterNumber,
            @Schema(example = "2026-27") String academicSessionCode,
            long enrolledCount,
            long completedCount,
            long withdrawnCount) {
    }

    public record AdminCourseEnrollmentReportPage(List<AdminCourseEnrollmentReportRow> items, int page, int size,
            long totalElements, int totalPages) {
    }
}
