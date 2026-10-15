package com.stacc.backend.lms.api;

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
public class LmsStudentContentController {

    private final LmsStudentContentQueryService contentQueryService;

    public LmsStudentContentController(LmsStudentContentQueryService contentQueryService) {
        this.contentQueryService = contentQueryService;
    }

    // The same rule as the student course routes. Whether this student may open this course, and which
    // content is published, is decided by the query, every time.
    @GetMapping("/courses/{lmsCourseId}/content")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')")
    @Operation(
            summary = "Get the published content of an accessible LMS course",
            description = "Returns the published modules of the course, each with its published topics, in course "
                    + "order, if the signed-in student may currently use the course. Drafts and archived content "
                    + "are never included. The student is taken from the access token.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The published content. The module list may be empty.")
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
            description = "The course does not exist or is not available to this student. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsCourseContentResponse content(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return contentQueryService.getPublishedContent(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }
}
