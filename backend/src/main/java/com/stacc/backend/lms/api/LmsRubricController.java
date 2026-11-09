package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.assignment.LmsAssignmentManagementService;
import com.stacc.backend.lms.assignment.rubric.LmsRubric;
import com.stacc.backend.lms.assignment.rubric.LmsRubricService;
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

/** The rubrics of a course, and which rubric an assignment is graded with, for the faculty who teach it. */
@RestController
@RequestMapping("/api/v1/lms/faculty")
@Tag(name = "LMS rubrics")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course, rubric, or assignment is not available to the account.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsRubricController {

    private static final String MANAGE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_ASSIGNMENT_MANAGE')";

    private final LmsRubricService rubrics;
    private final LmsAssignmentManagementService assignments;

    public LmsRubricController(LmsRubricService rubrics, LmsAssignmentManagementService assignments) {
        this.rubrics = rubrics;
        this.assignments = assignments;
    }

    /** The rubric an assignment is graded with; null for none. */
    public record AssignmentRubricRequest(@Schema(description = "An active rubric of the same course, or null for none.") Long rubricId) {
    }

    @GetMapping("/courses/{lmsCourseId}/rubrics")
    @PreAuthorize(MANAGE)
    @Operation(summary = "List a course's rubrics", description = "Archived ones included, newest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The rubrics.")
    public List<LmsRubricResponse> list(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        LmsRubricService.RubricList list = rubrics.list(AuthenticatedAccount.idFrom(token), lmsCourseId);
        return list.rubrics().stream().map(rubric -> LmsRubricResponse.of(rubric, list.inUse().contains(rubric.getId()))).toList();
    }

    @PostMapping("/courses/{lmsCourseId}/rubrics")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a rubric", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The rubric.")
    @ApiResponse(responseCode = "400", description = "A criterion or level does not fit.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsRubricResponse create(@PathVariable Long lmsCourseId, @Valid @RequestBody LmsRubricRequest request,
            @AuthenticationPrincipal Jwt token) {
        return LmsRubricResponse.of(rubrics.create(AuthenticatedAccount.idFrom(token), lmsCourseId, request.content()), false);
    }

    @PutMapping("/rubrics/{rubricId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Edit a rubric", description = "Replaces its criteria; refused once it has been used for grading.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The rubric.")
    @ApiResponse(responseCode = "409", description = "The rubric is archived or already used for grading.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsRubricResponse edit(@PathVariable Long rubricId, @Valid @RequestBody LmsRubricRequest request,
            @AuthenticationPrincipal Jwt token) {
        return LmsRubricResponse.of(rubrics.edit(AuthenticatedAccount.idFrom(token), rubricId, request.content()), false);
    }

    @PostMapping("/rubrics/{rubricId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Archive a rubric", description = "Assignments that use it keep it. Nothing deletes or restores a rubric.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The rubric.")
    public LmsRubricResponse archive(@PathVariable Long rubricId, @AuthenticationPrincipal Jwt token) {
        LmsRubric rubric = rubrics.archive(AuthenticatedAccount.idFrom(token), rubricId);
        return LmsRubricResponse.of(rubric, rubrics.isInUse(rubricId));
    }

    @PutMapping("/assignments/{assignmentId}/rubric")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Choose an assignment's rubric",
            description = "An active rubric of the same course whose total equals the maximum marks, or none.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The assignment.")
    @ApiResponse(responseCode = "409", description = "Not an active rubric of this course, or its total differs from the maximum marks.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyAssignmentResponse useRubric(@PathVariable Long assignmentId, @Valid @RequestBody AssignmentRubricRequest request,
            @AuthenticationPrincipal Jwt token) {
        return LmsFacultyAssignmentResponse.of(assignments.useRubric(AuthenticatedAccount.idFrom(token), assignmentId, request.rubricId()));
    }
}
