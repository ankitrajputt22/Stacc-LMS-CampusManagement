package com.stacc.backend.analytics;

import java.util.List;

import com.stacc.backend.analytics.AnalyticsService.CourseAnalytics;
import com.stacc.backend.analytics.AnalyticsService.StudentAnalytics;
import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.report.ReportActor;
import com.stacc.backend.report.export.ReportExport;
import com.stacc.backend.report.export.ReportExportService;
import com.stacc.backend.report.export.ReportExportService.ExportView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A student's own analytics, faculty course analytics, and background exports of reports one may already read. */
@RestController
@Tag(name = "Analytics and exports")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AnalyticsController {

    private static final String EXPORT = "hasAnyRole('FACULTY', 'ADMIN') and hasAuthority('REPORT_EXPORT')";

    public record ExportRequest(@NotNull ReportExport.Kind kind, Long parameter) {
    }

    private final AnalyticsService analytics;
    private final ReportExportService exports;
    private final DepartmentAnalyticsService departments;
    private final AreaAnalyticsService areas;

    public AnalyticsController(AnalyticsService analytics, ReportExportService exports, DepartmentAnalyticsService departments,
            AreaAnalyticsService areas) {
        this.departments = departments;
        this.areas = areas;
        this.analytics = analytics;
        this.exports = exports;
    }

    @GetMapping("/api/v1/analytics/student/me")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('ANALYTICS_VIEW_OWN')")
    @Operation(summary = "Your own analytics", description = "Attendance, assignment work and marks, and published results, as percentages.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public StudentAnalytics mine(@AuthenticationPrincipal Jwt token) {
        return analytics.forStudent(AuthenticatedAccount.idFrom(token));
    }

    @GetMapping("/api/v1/analytics/faculty/courses/{lmsCourseId}")
    @PreAuthorize("hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('COURSE_ANALYTICS_VIEW')")
    @Operation(summary = "Class analytics of a course you teach",
            description = "Attendance, assignments, quiz questions, and required pages. Percentages and averages only for groups of at least five; no student is listed or ranked.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public CourseAnalytics course(@AuthenticationPrincipal Jwt token, @PathVariable Long lmsCourseId) {
        return analytics.forCourse(AuthenticatedAccount.idFrom(token), lmsCourseId);
    }

    @PostMapping("/api/v1/exports")
    @PreAuthorize(EXPORT)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start a background export",
            description = "ADMIN_ENROLLMENTS needs ADMIN with ADMIN_REPORT_VIEW; COURSE_ANALYTICS needs a course you teach (parameter: its LMS course ID). "
                    + "The XLSX file is ready shortly and expires after a day.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ExportView start(@AuthenticationPrincipal Jwt token, Authentication authentication, @Valid @RequestBody ExportRequest request) {
        var held = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        boolean allowed = switch (request.kind()) {
            case ADMIN_ENROLLMENTS -> held.contains("ROLE_ADMIN") && held.contains("ADMIN_REPORT_VIEW");
            case COURSE_ANALYTICS -> held.contains("ROLE_FACULTY") && held.contains("COURSE_ANALYTICS_VIEW") && request.parameter() != null;
        };
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot export this report.");
        }
        return exports.request(AuthenticatedAccount.idFrom(token), request.kind(), request.parameter());
    }

    @GetMapping("/api/v1/analytics/department")
    @PreAuthorize("hasRole('HOD') and hasAuthority('DEPARTMENT_ANALYTICS_VIEW')")
    @Operation(summary = "My department's analytics", description = "For each department the account heads now: current-session students by program and, "
            + "per offering, enrolled students, finalized attendance (for groups of at least five), and published results. No student is named.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<DepartmentAnalyticsService.DepartmentView> department(@AuthenticationPrincipal Jwt token) {
        return departments.forHead(AuthenticatedAccount.idFrom(token));
    }

    @GetMapping("/api/v1/analytics/areas")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN', 'PLACEMENT_OFFICER') and hasAuthority('AREA_ANALYTICS_VIEW')")
    @Operation(summary = "Area analytics", description = "Live counts for hostel, helpdesk, campus engagement, placement, library, and AI course indexes; "
            + "each section only with that area's own permission, left out otherwise.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public AreaAnalyticsService.Areas areas(Authentication authentication) {
        return areas.forActor(ReportActor.of(null, authentication.getAuthorities()));
    }

    @GetMapping("/api/v1/exports")
    @PreAuthorize(EXPORT)
    @Operation(summary = "Your recent exports", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<ExportView> mine(@AuthenticationPrincipal Jwt token, Authentication authentication) {
        return exports.mine(AuthenticatedAccount.idFrom(token));
    }

    @GetMapping("/api/v1/exports/{exportId}/download")
    @PreAuthorize(EXPORT)
    @Operation(summary = "Download your export", description = "Only yours, only until it expires; each download is audited.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal Jwt token, @PathVariable Long exportId) {
        ReportExportService.Download file = exports.download(AuthenticatedAccount.idFrom(token), exportId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(new InputStreamResource(file.content()));
    }
}
