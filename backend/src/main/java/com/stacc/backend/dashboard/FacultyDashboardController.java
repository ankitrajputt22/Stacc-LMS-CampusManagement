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
 * The faculty dashboard. The route needs the faculty role and {@code FACULTY_DASHBOARD_VIEW}; what it shows is still
 * decided by official teaching assignments and the existing LMS and attendance rules, for the account in the access
 * token. It takes no parameters.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class FacultyDashboardController {

    private final FacultyDashboardQueryService dashboard;

    public FacultyDashboardController(FacultyDashboardQueryService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/faculty")
    @PreAuthorize("hasRole('FACULTY') and hasAuthority('FACULTY_DASHBOARD_VIEW')")
    @Operation(
            summary = "Get the signed-in faculty member's dashboard",
            description = "Returns short previews with full counts: the course offerings taught through active teaching assignments, with their LMS course state (offerings without an LMS course and archived ones included); upcoming draft or published assignments of their draft or published LMS courses, nearest deadline first; the grading queue, meaning submissions to published assignments whose latest attempt has no grade, longest-waiting first; and open attendance sessions, latest class first, with their unmarked counts. The faculty member is taken from the access token, so the request has no parameters. A section without data is empty, not an error.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The faculty member's dashboard.")
    @ApiResponse(
            responseCode = "401",
            description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public FacultyDashboardResponse facultyDashboard(@AuthenticationPrincipal Jwt accessToken) {
        return dashboard.getDashboard(AuthenticatedAccount.idFrom(accessToken));
    }
}
