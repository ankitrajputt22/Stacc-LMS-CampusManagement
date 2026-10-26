package com.stacc.backend.report.api;

import java.nio.charset.StandardCharsets;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.report.AdminReportQueryService;
import com.stacc.backend.report.AdminReports.AdminCourseEnrollmentReportPage;
import com.stacc.backend.report.AdminReports.AdminReportFilters;
import com.stacc.backend.report.AdminReports.AdminReportOverview;
import com.stacc.backend.report.ReportActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin reports: academic and enrollment counts within optional filters, a per-offering enrollment report and its CSV,
 * and operational counts for the modules the admin may manage. Every route needs the ADMIN role with ADMIN_REPORT_VIEW.
 * Read-only; nothing is stored.
 */
@RestController
@RequestMapping("/api/reports/admin")
@Tag(name = "Reports")
@ApiResponse(
        responseCode = "400",
        description = "A filter does not exist or contradicts another one, or the page or size is not valid.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account needs the ADMIN role with ADMIN_REPORT_VIEW.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminReportController {

    static final String CSV_FILE_NAME = "stacc-enrollment-report.csv";

    private final AdminReportQueryService reports;

    public AdminReportController(AdminReportQueryService reports) {
        this.reports = reports;
    }

    @GetMapping("/overview")
    @PreAuthorize(ReportAccess.ADMIN)
    @Operation(summary = "Get the admin report overview",
            description = "Department, program, and catalog course counts (college-wide), course offerings and semester and course enrollments by status within the optional filters, and college-wide operational counts: helpdesk tickets by status (needs HELPDESK_TICKET_VIEW_ALL), hostel allocations by status (needs HOSTEL_MANAGE), and campus service requests by status (needs CAMPUS_SERVICE_MANAGE). A section the admin may not see is null.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The overview.")
    public AdminReportOverview overview(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @Parameter(description = "Only this academic session.") @RequestParam(required = false) Long academicSessionId,
            @Parameter(description = "Only this program.") @RequestParam(required = false) Long programId,
            @Parameter(description = "Only this semester; it must belong to the program and session when those are given.")
            @RequestParam(required = false) Long semesterId) {
        AdminReportFilters filters = reports.filters(academicSessionId, programId, semesterId);
        return reports.overview(ReportActor.of(AuthenticatedAccount.idFrom(accessToken), authentication.getAuthorities()),
                filters);
    }

    @GetMapping("/enrollments")
    @PreAuthorize(ReportAccess.ADMIN)
    @Operation(summary = "Get the course enrollment report",
            description = "One row per course offering within the optional filters, with its course enrollments by status, in the admin offering list's order: the latest session first, then program, semester, and course code.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of rows. The list may be empty.")
    public AdminCourseEnrollmentReportPage enrollments(
            @Parameter(description = "Only this academic session.") @RequestParam(required = false) Long academicSessionId,
            @Parameter(description = "Only this program.") @RequestParam(required = false) Long programId,
            @Parameter(description = "Only this semester.") @RequestParam(required = false) Long semesterId,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return reports.enrollments(reports.filters(academicSessionId, programId, semesterId), page, size);
    }

    @GetMapping(value = "/enrollments/export.csv", produces = "text/csv")
    @PreAuthorize(ReportAccess.ADMIN)
    @Operation(summary = "Export the course enrollment report as CSV",
            description = "Every row of the course enrollment report under the same filters and rules, as a CSV download (stacc-enrollment-report.csv) with the columns Academic Session, Program, Semester, Course Code, Course Name, Enrolled, Completed, Withdrawn. No IDs. Cells are quoted, and cells that a spreadsheet would read as a formula are written as text. Nothing is stored on the server.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The CSV file.", content = @Content(mediaType = "text/csv"))
    public ResponseEntity<byte[]> enrollmentCsv(
            @Parameter(description = "Only this academic session.") @RequestParam(required = false) Long academicSessionId,
            @Parameter(description = "Only this program.") @RequestParam(required = false) Long programId,
            @Parameter(description = "Only this semester.") @RequestParam(required = false) Long semesterId) {
        String csv = reports.enrollmentCsv(reports.filters(academicSessionId, programId, semesterId));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(CSV_FILE_NAME).build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
