package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.copy.LmsCourseCopyService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Faculty copy course content from an earlier offering of the same course that they taught. */
@RestController
@RequestMapping("/api/v1/lms/faculty/courses/{lmsCourseId}")
@Tag(name = "LMS course copy")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course, or the chosen source, is not one the faculty member may use. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsCourseCopyController {

    private static final String MANAGE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_CONTENT_MANAGE')";

    private final LmsCourseCopyService copies;

    public LmsCourseCopyController(LmsCourseCopyService copies) {
        this.copies = copies;
    }

    @GetMapping("/copy-sources")
    @PreAuthorize(MANAGE)
    @Operation(summary = "List the courses this course can copy content from",
            description = "Earlier offerings of the same catalog course that the faculty member taught, archived ones "
                    + "included, the latest term first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The sources. The list may be empty.")
    public List<LmsCopySourceResponse> sources(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return copies.sources(AuthenticatedAccount.idFrom(token), lmsCourseId).stream().map(LmsCopySourceResponse::of).toList();
    }

    @PostMapping("/copy")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Copy content from an earlier offering",
            description = "Copies modules, pages, study files, and the syllabus text (when this course has none) as drafts "
                    + "after this course's own content. Assignments, announcements, and anything about students are not copied.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "How much was copied.")
    @ApiResponse(responseCode = "503", description = "File storage did not answer; nothing was copied.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsCourseCopyService.CopyResult copy(@PathVariable Long lmsCourseId, @Valid @RequestBody LmsCourseCopyRequest request,
            @AuthenticationPrincipal Jwt token) {
        return copies.copy(AuthenticatedAccount.idFrom(token), lmsCourseId, request.sourceLmsCourseId());
    }
}
