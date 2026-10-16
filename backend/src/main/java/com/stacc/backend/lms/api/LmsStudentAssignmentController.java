package com.stacc.backend.lms.api;

import java.util.List;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The assignments of a course, as its students see them: published ones only. The student is taken from
 * the access token, and course access is decided again on every request from official enrollment.
 */
@RestController
@RequestMapping("/api/lms/courses/{lmsCourseId}/assignments")
@Tag(name = "LMS")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsStudentAssignmentController {

    // The same rule as the other student course routes. It only lets the caller ask; which courses and
    // assignments they get is decided by the query.
    private static final String STUDENT_VIEW = "hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')";

    private final LmsStudentAssignmentQueryService queryService;

    public LmsStudentAssignmentController(LmsStudentAssignmentQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize(STUDENT_VIEW)
    @Operation(
            summary = "Get the published assignments of an accessible LMS course",
            description = "Returns the course's published assignments, earliest deadline first, if the signed-in student "
                    + "may currently use the course. Assignments whose deadline has passed are included. Drafts and "
                    + "archived assignments are never included.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The published assignments. The list may be empty.")
    @ApiResponse(
            responseCode = "404",
            description = "The course does not exist or is not available to this student. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<LmsCourseAssignmentResponse> assignments(
            @PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return queryService.getPublishedAssignments(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }

    @GetMapping("/{assignmentId}")
    @PreAuthorize(STUDENT_VIEW)
    @Operation(
            summary = "Get one published assignment of an accessible LMS course",
            description = "Returns the assignment's title, instructions, deadline, and maximum marks, with the course "
                    + "code and name. Only a published assignment of this course is returned, and only to a student "
                    + "who may currently use the course.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The assignment.")
    @ApiResponse(
            responseCode = "404",
            description = "The assignment does not exist, is not published, belongs to another course, or the course is "
                    + "not available to this student. All look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsCourseAssignmentDetailResponse assignment(
            @PathVariable Long lmsCourseId, @PathVariable Long assignmentId, @AuthenticationPrincipal Jwt accessToken) {
        return queryService.getPublishedAssignment(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, assignmentId);
    }
}
