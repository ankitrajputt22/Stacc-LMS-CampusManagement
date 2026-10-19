package com.stacc.backend.dashboard;

import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin dashboard. The route needs the admin role and {@code ADMIN_DASHBOARD_VIEW}. It only reads and takes no
 * parameters; it gives no right to change masters, offerings, or enrollments.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class AdminDashboardController {

    private final AdminDashboardQueryService dashboard;

    public AdminDashboardController(AdminDashboardQueryService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_DASHBOARD_VIEW')")
    @Operation(
            summary = "Get the admin dashboard",
            description = "Returns exact counts of departments, programs, academic sessions, semesters, and courses (every record, whatever its status), of course offerings, and of semester and course enrollments that are ENROLLED, with the first five course offerings in the admin list's order. Calculated on each request and never stored. The request has no parameters.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The admin dashboard.")
    @ApiResponse(
            responseCode = "401",
            description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminDashboardResponse adminDashboard() {
        return dashboard.getDashboard();
    }
}
