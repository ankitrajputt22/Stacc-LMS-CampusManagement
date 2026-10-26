package com.stacc.backend.report.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.report.FacultyReportQueryService;
import com.stacc.backend.report.FacultyReports.FacultyCourseReport;
import com.stacc.backend.report.FacultyReports.FacultyReportCourseSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Faculty reports: counts for the course offerings the signed-in faculty member actively teaches. The faculty member
 * always comes from the access token; no route takes a faculty, profile, or account ID. Read-only.
 */
@RestController
@RequestMapping("/api/reports/faculty")
@Tag(name = "Reports")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account needs the FACULTY role with FACULTY_REPORT_VIEW.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class FacultyReportController {

    private final FacultyReportQueryService reports;

    public FacultyReportController(FacultyReportQueryService reports) {
        this.reports = reports;
    }

    @GetMapping("/my-courses")
    @PreAuthorize(ReportAccess.FACULTY)
    @Operation(summary = "List my report courses",
            description = "Every course offering the signed-in faculty member teaches through an ACTIVE teaching assignment, with or without an LMS course, with ENROLLED course enrollments, draft and published assignments (when there is an LMS course faculty can use), finalized attendance sessions, and published results. Counts only.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The offerings. The list may be empty.")
    public List<FacultyReportCourseSummary> myCourses(@AuthenticationPrincipal Jwt accessToken) {
        return reports.myCourses(AuthenticatedAccount.idFrom(accessToken));
    }

    @GetMapping("/course-offerings/{courseOfferingId}")
    @PreAuthorize(ReportAccess.FACULTY)
    @Operation(summary = "Get a course offering report",
            description = "Enrollment, assignment and grading workload, attendance, and result counts for one course offering the signed-in faculty member actively teaches. Works without an LMS course: the assignment section is then marked unavailable. No individual student data and no averages, ranks, or pass rates.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The report.")
    @ApiResponse(responseCode = "404", description = "The offering does not exist, or the faculty member does not actively teach it.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public FacultyCourseReport courseReport(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long courseOfferingId) {
        return reports.courseReport(AuthenticatedAccount.idFrom(accessToken), courseOfferingId);
    }
}
