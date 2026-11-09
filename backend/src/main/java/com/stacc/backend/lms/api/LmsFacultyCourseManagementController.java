package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.management.LmsCourseManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Faculty management of the LMS courses of the course offerings they officially teach: list them, create a missing
 * LMS course, publish, and archive. Every route needs the faculty role,
 * {@code FACULTY_COURSE_VIEW}, and {@code LMS_COURSE_MANAGE}. Which offerings a faculty member manages is decided from
 * active teaching assignments, starting from the account in the access token; no route takes a faculty, profile, or
 * assignment ID. There is no delete and no restore.
 */
@RestController
@RequestMapping("/api/v1/lms/faculty")
@Tag(name = "LMS")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsFacultyCourseManagementController {

    static final String MANAGE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_COURSE_MANAGE')";

    private final LmsFacultyManagedCourseQueryService queryService;
    private final LmsCourseManagementService managementService;

    public LmsFacultyCourseManagementController(LmsFacultyManagedCourseQueryService queryService,
            LmsCourseManagementService managementService) {
        this.queryService = queryService;
        this.managementService = managementService;
    }

    @GetMapping("/managed-courses")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "List the course offerings the faculty member manages",
            description = "Returns every course offering the signed-in faculty member is actively assigned to teach, with its LMS course and status when it has one: offerings without an LMS course and archived LMS courses included. In course code order. The list may be empty.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The faculty member's teaching offerings.")
    public List<LmsFacultyManagedCourseResponse> managedCourses(@AuthenticationPrincipal Jwt accessToken) {
        return queryService.getManagedCourses(AuthenticatedAccount.idFrom(accessToken));
    }

    @PostMapping("/course-offerings/{courseOfferingId}/lms-course")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create the LMS course of a course offering",
            description = "Creates the DRAFT LMS course of an offering the faculty member teaches. Its currently enrolled students use it once it is published, worked out live from official enrollment. No enrollment, offering, or teaching assignment changes.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new LMS course and how many students are currently enrolled.")
    @ApiResponse(
            responseCode = "400",
            description = "The course offering ID is not a number.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The offering does not exist, or the faculty member does not teach it.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The offering already has an LMS course.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsCourseManagementResponse create(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long courseOfferingId) {
        return LmsCourseManagementResponse.of(managementService.create(AuthenticatedAccount.idFrom(accessToken), courseOfferingId));
    }

    @PostMapping("/courses/{lmsCourseId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Publish an LMS course",
            description = "Moves a DRAFT LMS course to PUBLISHED; a published one stays published. Students get in only while their official course and semester enrollments are ENROLLED and their profile is ACTIVE.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The published LMS course.")
    @ApiResponse(
            responseCode = "400",
            description = "The LMS course ID is not a number.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The LMS course does not exist, or the faculty member does not teach its offering.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The LMS course is archived and cannot be published again.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsCourseManagementResponse publish(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long lmsCourseId) {
        return LmsCourseManagementResponse.of(managementService.publish(AuthenticatedAccount.idFrom(accessToken), lmsCourseId));
    }

    @PutMapping("/courses/{lmsCourseId}/home")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Choose what the course home shows",
            description = "Sets the course home to the overview, the modules, or the syllabus, and its optional welcome "
                    + "message in formatted text. Official course details come from the academic records and cannot change here.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The course home.")
    @ApiResponse(responseCode = "400", description = "The choice is missing or the message is too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The LMS course does not exist, or the faculty member does not teach its offering.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The LMS course is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsCourseHomeResponse changeHome(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long lmsCourseId,
            @Valid @RequestBody LmsCourseHomeRequest request) {
        return LmsCourseHomeResponse.of(managementService.changeHome(AuthenticatedAccount.idFrom(accessToken), lmsCourseId,
                request.homeView(), request.welcomeMessage()));
    }

    @PostMapping("/courses/{lmsCourseId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Archive an LMS course",
            description = "Moves a DRAFT or PUBLISHED LMS course to ARCHIVED; an archived one stays archived. Students and the normal faculty pages lose access, and its content, assignments, submissions, and grades are all kept. There is no restore.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The archived LMS course.")
    @ApiResponse(
            responseCode = "400",
            description = "The LMS course ID is not a number.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The LMS course does not exist, or the faculty member does not teach its offering.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsCourseManagementResponse archive(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long lmsCourseId) {
        return LmsCourseManagementResponse.of(managementService.archive(AuthenticatedAccount.idFrom(accessToken), lmsCourseId));
    }
}
