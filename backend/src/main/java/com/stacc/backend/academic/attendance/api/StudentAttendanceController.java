package com.stacc.backend.academic.attendance.api;

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

@RestController
@RequestMapping("/api/attendance/student")
@Tag(name = "Attendance")
public class StudentAttendanceController {

    private final StudentAttendanceQueryService queryService;

    public StudentAttendanceController(StudentAttendanceQueryService queryService) {
        this.queryService = queryService;
    }

    // The rule lets in a student account that may view attendance. Whose records are returned is decided by the
    // query, from the account in the token and that student's own course enrollments.
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('ATTENDANCE_VIEW')")
    @Operation(
            summary = "Get the authenticated student's own attendance",
            description = "Returns the signed-in student's finalized attendance, one entry per course offering that has "
                    + "at least one finalized session, with present, absent, and session counts, the percentage present, "
                    + "and each session latest first. Open sessions are never included. The student is taken from the "
                    + "access token, so the request has no parameters.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The student's attendance. The list of courses may be empty.")
    @ApiResponse(
            responseCode = "401",
            description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public StudentAttendanceResponse myAttendance(@AuthenticationPrincipal Jwt accessToken) {
        return queryService.getMyAttendance(AuthenticatedAccount.idFrom(accessToken));
    }
}
