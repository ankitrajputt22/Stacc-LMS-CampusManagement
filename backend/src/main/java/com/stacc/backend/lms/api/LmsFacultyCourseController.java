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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lms/faculty")
@Tag(name = "LMS")
public class LmsFacultyCourseController {

    private final LmsFacultyCourseQueryService courseQueryService;

    public LmsFacultyCourseController(LmsFacultyCourseQueryService courseQueryService) {
        this.courseQueryService = courseQueryService;
    }

    // The rule below lets in a faculty account that may use the faculty course-view feature. Which
    // courses that faculty member actually gets is decided by the query, from official teaching assignments.
    @GetMapping("/my-courses")
    @PreAuthorize("hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW')")
    @Operation(
            summary = "Get the authenticated faculty member's accessible LMS courses",
            description = "Returns the LMS courses the signed-in faculty member is actively assigned to teach and may "
                    + "currently use, drafts included. The faculty member is taken from the access token, so the "
                    + "request has no parameters. The list may be empty.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The faculty member's current LMS courses, in course code order.")
    @ApiResponse(
            responseCode = "401",
            description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<LmsFacultyCourseSummaryResponse> myCourses(@AuthenticationPrincipal Jwt accessToken) {
        return courseQueryService.getAccessibleCourses(AuthenticatedAccount.idFrom(accessToken));
    }
}
