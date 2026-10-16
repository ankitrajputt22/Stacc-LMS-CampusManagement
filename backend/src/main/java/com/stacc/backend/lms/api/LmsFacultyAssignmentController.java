package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.assignment.LmsAssignmentManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assignments as faculty manage them. Reading uses the faculty course-view permission; every change also
 * needs the assignment permission. The faculty member is taken from the access token, and which courses
 * they may use is decided from official teaching assignments on every request. There is no delete.
 */
@RestController
@RequestMapping("/api/lms/faculty")
@Tag(name = "LMS")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsFacultyAssignmentController {

    private static final String FACULTY_VIEW = "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW')";
    private static final String MANAGE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_ASSIGNMENT_MANAGE')";

    private final LmsFacultyAssignmentQueryService queryService;
    private final LmsAssignmentManagementService managementService;

    public LmsFacultyAssignmentController(
            LmsFacultyAssignmentQueryService queryService, LmsAssignmentManagementService managementService) {
        this.queryService = queryService;
        this.managementService = managementService;
    }

    @GetMapping("/my-assignments")
    @PreAuthorize(FACULTY_VIEW)
    @Operation(
            summary = "Get the assignments of every course the faculty member teaches",
            description = "Returns the draft and published assignments of the LMS courses the signed-in faculty member "
                    + "is actively assigned to teach and may currently use, earliest deadline first, with each course's "
                    + "code and name. Archived assignments are kept but not listed. The request has no parameters.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The assignments. The list may be empty.")
    public List<LmsFacultyAssignmentOverviewResponse> myAssignments(@AuthenticationPrincipal Jwt accessToken) {
        return queryService.getMyAssignments(AuthenticatedAccount.idFrom(accessToken));
    }

    @GetMapping("/courses/{lmsCourseId}/assignments")
    @PreAuthorize(FACULTY_VIEW)
    @Operation(
            summary = "Get the assignments of a course the faculty member teaches",
            description = "Returns the draft and published assignments, earliest deadline first. Archived ones are kept "
                    + "but not listed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The assignments. The list may be empty.")
    @ApiResponse(
            responseCode = "404",
            description = "The course does not exist or is not available to this account. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<LmsFacultyAssignmentResponse> courseAssignments(
            @PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return queryService.getManagedAssignments(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }

    @PostMapping("/courses/{lmsCourseId}/assignments")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add an assignment",
            description = "Adds a draft assignment to the course. Students see it once it is published.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new assignment.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid, such as a deadline without a time zone or marks that are not "
                    + "greater than zero.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The course does not exist or is not available to this account. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyAssignmentResponse create(
            @PathVariable Long lmsCourseId,
            @Valid @RequestBody LmsAssignmentRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyAssignmentResponse.of(managementService.create(AuthenticatedAccount.idFrom(accessToken),
                lmsCourseId, request.title(), request.instructions(), request.dueAt(), request.maxMarks()));
    }

    @PutMapping("/assignments/{assignmentId}")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Edit an assignment",
            description = "Changes the title, instructions, deadline, and maximum marks of a draft or published "
                    + "assignment. A published one stays published, keeps its publication time, and students see the "
                    + "change at once. Nobody is notified.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed assignment.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The assignment does not exist or is not available to this account. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The assignment is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyAssignmentResponse update(
            @PathVariable Long assignmentId,
            @Valid @RequestBody LmsAssignmentRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyAssignmentResponse.of(managementService.update(AuthenticatedAccount.idFrom(accessToken),
                assignmentId, request.title(), request.instructions(), request.dueAt(), request.maxMarks()));
    }

    @PostMapping("/assignments/{assignmentId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Publish an assignment",
            description = "Shows the assignment to the course's students and records when that first happened. "
                    + "Publishing a published assignment changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The published assignment.")
    @ApiResponse(
            responseCode = "404",
            description = "The assignment does not exist or is not available to this account. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The assignment is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyAssignmentResponse publish(@PathVariable Long assignmentId, @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyAssignmentResponse.of(managementService.publish(AuthenticatedAccount.idFrom(accessToken), assignmentId));
    }

    @PostMapping("/assignments/{assignmentId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Archive an assignment",
            description = "Hides the assignment from students and from the faculty lists, and keeps it. Archiving an "
                    + "archived assignment changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The archived assignment.")
    @ApiResponse(
            responseCode = "404",
            description = "The assignment does not exist or is not available to this account. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyAssignmentResponse archive(@PathVariable Long assignmentId, @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyAssignmentResponse.of(managementService.archive(AuthenticatedAccount.idFrom(accessToken), assignmentId));
    }
}
