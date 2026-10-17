package com.stacc.backend.academic.result.api;

import java.util.List;

import com.stacc.backend.academic.result.CourseResultManagementService;
import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Official course results as faculty record them. Every route needs the faculty role and the result permission;
 * which course offerings the faculty member may use is decided from official teaching assignments on every
 * request, with no LMS course involved. The faculty member is taken from the access token. Results are never
 * calculated from LMS grades or attendance, and there is no delete and no unpublish.
 */
@RestController
@RequestMapping("/api/results/faculty")
@Tag(name = "Results")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class FacultyResultController {

    private static final String MANAGE = "hasRole('FACULTY') and hasAuthority('ACADEMIC_RESULT_MANAGE')";

    private final FacultyResultQueryService queryService;
    private final CourseResultManagementService managementService;

    public FacultyResultController(FacultyResultQueryService queryService, CourseResultManagementService managementService) {
        this.queryService = queryService;
        this.managementService = managementService;
    }

    @GetMapping("/my-courses")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Get the course offerings the faculty member records results for",
            description = "Returns the course offerings the signed-in faculty member is actively assigned to teach, in "
                    + "course code order, whether or not they have an LMS course. The request has no parameters.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The course offerings. The list may be empty.")
    public List<ResultCourseOfferingResponse> myCourses(@AuthenticationPrincipal Jwt accessToken) {
        return queryService.getMyCourseOfferings(AuthenticatedAccount.idFrom(accessToken));
    }

    @GetMapping("/course-offerings/{courseOfferingId}")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Get a course offering's enrollments and their results",
            description = "Returns the course offering and every official enrollment, withdrawn ones included, in "
                    + "student ID order, each with its result or null.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The roster with its results.")
    @ApiResponse(
            responseCode = "404",
            description = "The course offering does not exist or the faculty member does not teach it. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CourseResultRosterResponse roster(@PathVariable Long courseOfferingId, @AuthenticationPrincipal Jwt accessToken) {
        return queryService.getRoster(AuthenticatedAccount.idFrom(accessToken), courseOfferingId);
    }

    @PutMapping("/course-offerings/{courseOfferingId}/enrollments/{courseEnrollmentId}")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Save an enrollment's result",
            description = "Creates the enrollment's draft result, edits its draft, or corrects its published result, which "
                    + "stays published and keeps its first publication time; the student sees a correction at once. A new "
                    + "result needs an enrolled or completed enrollment. The enrollment must belong to the course offering.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The saved result.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid, such as marks above the maximum. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The course offering or enrollment does not exist, the enrollment belongs to another offering, "
                    + "or the faculty member does not teach it. All look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The enrollment is withdrawn and has no result yet, so a new one cannot be recorded.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public FacultyResultResponse save(
            @PathVariable Long courseOfferingId,
            @PathVariable Long courseEnrollmentId,
            @Valid @RequestBody CourseResultRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return FacultyResultResponse.of(managementService.save(AuthenticatedAccount.idFrom(accessToken), courseOfferingId,
                courseEnrollmentId, request.marksObtained(), request.maxMarks(), request.gradeCode(), request.remarks()));
    }

    @PostMapping("/course-offerings/{courseOfferingId}/enrollments/{courseEnrollmentId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Publish an enrollment's result",
            description = "Publishes the saved result so the student sees it, and records the first publication time. "
                    + "Publishing a published result changes nothing. There is no unpublish.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The published result.")
    @ApiResponse(
            responseCode = "404",
            description = "No result has been saved yet, or the course offering or enrollment is not available to this "
                    + "account.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public FacultyResultResponse publish(
            @PathVariable Long courseOfferingId,
            @PathVariable Long courseEnrollmentId,
            @AuthenticationPrincipal Jwt accessToken) {
        return FacultyResultResponse.of(
                managementService.publish(AuthenticatedAccount.idFrom(accessToken), courseOfferingId, courseEnrollmentId));
    }
}
