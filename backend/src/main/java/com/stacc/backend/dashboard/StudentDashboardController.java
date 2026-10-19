package com.stacc.backend.dashboard;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The student dashboard. The route needs the student role and {@code STUDENT_DASHBOARD_VIEW}; what it shows is still
 * decided by the course, attendance, and result rules for the account in the access token. It takes no parameters.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class StudentDashboardController {

    private final StudentDashboardQueryService dashboard;

    public StudentDashboardController(StudentDashboardQueryService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('STUDENT_DASHBOARD_VIEW')")
    @Operation(
            summary = "Get the signed-in student's dashboard",
            description = "Returns short previews with full counts: current LMS courses (the same rule as the student's course list), upcoming published assignments of those courses (due now or later, nearest first), recent published announcements of those courses (newest first), finalized attendance per course offering, and published results (most recently published first). The student is taken from the access token, so the request has no parameters. A section without data is empty, not an error.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The student's dashboard.")
    @ApiResponse(
            responseCode = "401",
            description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public StudentDashboardResponse studentDashboard(@AuthenticationPrincipal Jwt accessToken) {
        return dashboard.getDashboard(AuthenticatedAccount.idFrom(accessToken));
    }
}
