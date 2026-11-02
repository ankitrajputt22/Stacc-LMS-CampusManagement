package com.stacc.backend.academic.admin;

import com.stacc.backend.academic.admin.EnrollmentMonitorService.Filters;
import com.stacc.backend.academic.admin.EnrollmentMonitorService.MonitorPage;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The admin enrollment monitor: every course enrollment, filtered and paged on the server. Read-only. */
@RestController
@RequestMapping("/api/v1/admin/academic/enrollments")
@Tag(name = "Admin enrollments")
public class AdminEnrollmentMonitorController {

    private final EnrollmentMonitorService monitor;

    public AdminEnrollmentMonitorController(EnrollmentMonitorService monitor) {
        this.monitor = monitor;
    }

    @GetMapping("/course-enrollments")
    @PreAuthorize(AdminAcademicAccess.ENROLLMENT_VIEW)
    @Operation(
            summary = "Search course enrollments",
            description = "One page of official course enrollments, whatever their status, matching every filter "
                    + "given: session, program, semester, the student's section, course, and status. The search text "
                    + "matches the student ID, roll number, name, or course code. Latest session first, then program, "
                    + "semester, course, and student ID. Only academic details are returned, never contact details.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The page; it may be empty.")
    @ApiResponse(responseCode = "400", description = "A filter is not valid, the text is over 100 characters, or the "
            + "page is out of range.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public MonitorPage search(
            @RequestParam(required = false) Long academicSessionId,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) CourseEnrollmentStatus status,
            @Parameter(description = "Part of a student ID, roll number, name, or course code.")
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return monitor.search(new Filters(academicSessionId, programId, semesterId, sectionId, courseId, status, q),
                page, size);
    }
}
