package com.stacc.backend.lms.api;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.syllabus.LmsSyllabusService;
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
 * Course syllabi. Students read the published version of a course they may use; faculty read and change the draft of a
 * course they teach and publish it. The caller always comes from the access token.
 */
@RestController
@RequestMapping("/api/v1/lms")
@Tag(name = "LMS syllabus")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course does not exist or is not available to the account. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsSyllabusController {

    private static final String STUDENT = "hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')";
    private static final String FACULTY_VIEW = "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW')";
    private static final String MANAGE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_CONTENT_MANAGE')";

    private final LmsSyllabusService syllabi;

    public LmsSyllabusController(LmsSyllabusService syllabi) {
        this.syllabi = syllabi;
    }

    @GetMapping("/courses/{lmsCourseId}/syllabus")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Read the published syllabus of an accessible LMS course",
            description = "Returns the published version, or nulls when none is published yet. Drafts never appear.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The syllabus.")
    public LmsCourseSyllabusResponse forStudent(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return LmsCourseSyllabusResponse.of(syllabi.forStudent(AuthenticatedAccount.idFrom(token), lmsCourseId).orElse(null));
    }

    @GetMapping("/faculty/courses/{lmsCourseId}/syllabus")
    @PreAuthorize(FACULTY_VIEW)
    @Operation(summary = "Read the syllabus of a course the faculty member teaches",
            description = "Returns the draft and the published version, or nulls when none is saved yet.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The syllabus.")
    public LmsFacultySyllabusResponse forFaculty(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return LmsFacultySyllabusResponse.of(syllabi.forFaculty(AuthenticatedAccount.idFrom(token), lmsCourseId).orElse(null));
    }

    @PutMapping("/faculty/courses/{lmsCourseId}/syllabus")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Save the syllabus draft",
            description = "Saves the draft, starting the course's syllabus when there is none. Students keep reading "
                    + "the published version until it is published again.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The syllabus.")
    @ApiResponse(responseCode = "400", description = "The text is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultySyllabusResponse saveDraft(@PathVariable Long lmsCourseId, @Valid @RequestBody LmsSyllabusRequest request,
            @AuthenticationPrincipal Jwt token) {
        return LmsFacultySyllabusResponse.of(syllabi.saveDraft(AuthenticatedAccount.idFrom(token), lmsCourseId, request.content()));
    }

    @PostMapping("/faculty/courses/{lmsCourseId}/syllabus/publish")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Publish the syllabus draft",
            description = "Makes the saved draft the version students read. Publishing it again without changes "
                    + "changes nothing. There is no unpublish.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The syllabus.")
    @ApiResponse(responseCode = "409", description = "No draft has been saved yet.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultySyllabusResponse publish(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return LmsFacultySyllabusResponse.of(syllabi.publish(AuthenticatedAccount.idFrom(token), lmsCourseId));
    }
}
