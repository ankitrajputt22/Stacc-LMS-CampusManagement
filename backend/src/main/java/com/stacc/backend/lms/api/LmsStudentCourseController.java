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

@RestController
@RequestMapping("/api/lms")
@Tag(name = "LMS")
public class LmsStudentCourseController {

    private final LmsStudentCourseQueryService courseQueryService;

    public LmsStudentCourseController(LmsStudentCourseQueryService courseQueryService) {
        this.courseQueryService = courseQueryService;
    }

    // The rule below lets in a student account that may use the course-view feature. Which courses
    // that student actually gets is a separate question, answered by the query from official records.
    @GetMapping("/my-courses")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')")
    @Operation(
            summary = "Get the authenticated student's accessible LMS courses",
            description = "Returns the LMS courses the signed-in student can currently use. The student is "
                    + "taken from the access token, so the request has no parameters. The list may be empty.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The student's current LMS courses, in course code order.")
    @ApiResponse(
            responseCode = "401",
            description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<LmsCourseSummaryResponse> myCourses(@AuthenticationPrincipal Jwt accessToken) {
        return courseQueryService.getAccessibleCourses(AuthenticatedAccount.idFrom(accessToken));
    }

    // The same rule as the course list: a student account that may use the course-view feature.
    // Whether this student may open this particular course is decided by the query, every time.
    @GetMapping("/courses/{lmsCourseId}")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')")
    @Operation(
            summary = "Get an accessible LMS course",
            description = "Returns one LMS course that the signed-in student can currently use. The student is "
                    + "taken from the access token. A course that does not exist and a course the student "
                    + "cannot use both answer 404.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The course and its academic details.")
    @ApiResponse(
            responseCode = "401",
            description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The course does not exist or is not available to this student.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsCourseDetailResponse course(
            @PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return courseQueryService.getAccessibleCourse(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }
}
