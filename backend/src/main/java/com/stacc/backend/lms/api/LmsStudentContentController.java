package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.content.LmsPageCompletionService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lms")
@Tag(name = "LMS")
public class LmsStudentContentController {

    private final LmsStudentContentQueryService contentQueryService;
    private final LmsPageCompletionService completions;

    public LmsStudentContentController(LmsStudentContentQueryService contentQueryService,
            LmsPageCompletionService completions) {
        this.contentQueryService = contentQueryService;
        this.completions = completions;
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

    @GetMapping("/courses/{lmsCourseId}/progress")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')")
    @Operation(
            summary = "Read one's own progress in an accessible LMS course",
            description = "Required pages and how many of them the signed-in student completed, with the next required "
                    + "page to do in a module that is open to them. Worked out every time; nothing is stored.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The progress.")
    @ApiResponse(
            responseCode = "404",
            description = "The course does not exist or is not available to this student. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsCourseProgressResponse progress(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return contentQueryService.getProgress(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }

    @PostMapping("/courses/{lmsCourseId}/pages/{pageId}/complete")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')")
    @Operation(
            summary = "Mark a page complete",
            description = "Records once that the signed-in student completed a page they can see, in a module that is "
                    + "open to them, and answers their progress. Marking it again changes nothing; nothing undoes it.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The student's progress after the change.")
    @ApiResponse(
            responseCode = "404",
            description = "The course or page does not exist, or is not available to this student. All look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The page's module is locked until an earlier module is completed.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsCourseProgressResponse complete(@PathVariable Long lmsCourseId, @PathVariable Long pageId,
            @AuthenticationPrincipal Jwt accessToken) {
        Long account = AuthenticatedAccount.idFrom(accessToken);
        completions.complete(account, lmsCourseId, pageId);
        return contentQueryService.getProgress(account, lmsCourseId);
    }

    @GetMapping("/courses/{lmsCourseId}/files")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')")
    @Operation(
            summary = "List the published files of an accessible LMS course",
            description = "Returns every published study material of the course, in course order, with the page and "
                    + "module it is on, if the signed-in student may currently use the course. Files download through "
                    + "the material download route. The student is taken from the access token.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The files. The list may be empty.")
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
    public List<LmsCourseFileResponse> files(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return contentQueryService.getPublishedFiles(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }

    @GetMapping("/courses/{lmsCourseId}/pages/{pageId}")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')")
    @Operation(
            summary = "Open one published page of an accessible LMS course",
            description = "Returns one published page (a topic of a published module) with its published study "
                    + "materials and the published pages before and after it, if the signed-in student may currently "
                    + "use the course. The student is taken from the access token.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The page.")
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
            description = "The course or page does not exist, or is not available to this student. All look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsCoursePageResponse page(@PathVariable Long lmsCourseId, @PathVariable Long pageId,
            @AuthenticationPrincipal Jwt accessToken) {
        return contentQueryService.getPublishedPage(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, pageId);
    }
}
