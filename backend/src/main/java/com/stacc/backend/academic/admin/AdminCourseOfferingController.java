package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingUpdate;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseOfferingResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.OfferingRosterResponse;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin management of course offerings: one catalog course taught in one semester. Reading needs the admin role and
 * {@code ACADEMIC_OFFERING_VIEW}; creating and editing also need {@code ACADEMIC_OFFERING_MANAGE}. The roster is
 * enrollment data and needs {@code ACADEMIC_ENROLLMENT_VIEW}. There is no delete.
 */
@RestController
@RequestMapping("/api/admin/academic/offerings")
@Tag(name = "Admin course offerings")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminCourseOfferingController {

    private final CourseOfferingManagementService offerings;
    private final AdminEnrollmentQueryService enrollments;

    public AdminCourseOfferingController(CourseOfferingManagementService offerings, AdminEnrollmentQueryService enrollments) {
        this.offerings = offerings;
        this.enrollments = enrollments;
    }

    @GetMapping
    @PreAuthorize(AdminAcademicAccess.OFFERING_VIEW)
    @Operation(
            summary = "List course offerings",
            description = "Returns course offerings with their course, semester, program, and session: the latest session first, then program code, semester number, and course code. Each filter that is sent narrows the list.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The offerings. The list may be empty.")
    @ApiResponse(
            responseCode = "400",
            description = "A filter is not a number.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<CourseOfferingResponse> list(
            @Parameter(description = "Only offerings of this semester.") @RequestParam(required = false) Long semesterId,
            @Parameter(description = "Only offerings of this course.") @RequestParam(required = false) Long courseId,
            @Parameter(description = "Only offerings in this academic session.") @RequestParam(required = false) Long academicSessionId,
            @Parameter(description = "Only offerings of this program’s semesters.") @RequestParam(required = false) Long programId) {
        return offerings.list(semesterId, courseId, academicSessionId, programId);
    }

    @GetMapping("/{courseOfferingId}")
    @PreAuthorize(AdminAcademicAccess.OFFERING_VIEW)
    @Operation(summary = "Get one course offering", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The offering.")
    @ApiResponse(
            responseCode = "404",
            description = "The offering does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CourseOfferingResponse get(@PathVariable Long courseOfferingId) {
        return offerings.get(courseOfferingId);
    }

    @PostMapping
    @PreAuthorize(AdminAcademicAccess.OFFERING_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a course offering",
            description = "Offers an existing course in an existing semester. The new offering is PLANNED. Its course and semester can never be changed afterwards.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new offering.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The selected course or semester does not exist. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The course is already offered in this semester.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CourseOfferingResponse create(@Valid @RequestBody CourseOfferingCreate request) {
        return offerings.create(request);
    }

    @PutMapping("/{courseOfferingId}")
    @PreAuthorize(AdminAcademicAccess.OFFERING_MANAGE)
    @Operation(
            summary = "Edit a course offering",
            description = "Changes the status only. An offering moves from PLANNED to ACTIVE to CLOSED and never back. A course or semester that is sent must be the offering’s own: enrollments, teaching, attendance, results, and an LMS course depend on them.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed offering.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The offering does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "A different course or semester was sent, or the status cannot change that way, such as making a closed offering active again.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CourseOfferingResponse update(@PathVariable Long courseOfferingId, @Valid @RequestBody CourseOfferingUpdate request) {
        return offerings.update(courseOfferingId, request);
    }

    @GetMapping("/{courseOfferingId}/enrollments")
    @PreAuthorize(AdminAcademicAccess.ENROLLMENT_VIEW)
    @Operation(
            summary = "List an offering’s roster",
            description = "Returns the offering and every course enrollment of exactly this offering, whatever its status, by student login ID. Students appear only by profile ID and login ID.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The offering and its enrollments. The list may be empty.")
    @ApiResponse(
            responseCode = "404",
            description = "The offering does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public OfferingRosterResponse roster(@PathVariable Long courseOfferingId) {
        return enrollments.roster(courseOfferingId);
    }
}
