package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.announcement.LmsAnnouncementManagementService;
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

/**
 * Course announcements. Students read the published ones; faculty manage them with the existing content
 * permission. Every route takes the caller from the access token, and which course they may use is
 * decided from official enrollment or teaching assignments on every request. There is no delete.
 */
@RestController
@RequestMapping("/api/lms")
@Tag(name = "LMS")
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
        description = "The course or announcement does not exist or is not available to this account. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsAnnouncementController {

    private static final String STUDENT_VIEW = "hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')";
    private static final String FACULTY_VIEW = "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW')";
    private static final String MANAGE = "hasRole('FACULTY') and hasAuthority('LMS_CONTENT_MANAGE')";

    private final LmsAnnouncementQueryService queryService;
    private final LmsAnnouncementManagementService managementService;

    public LmsAnnouncementController(
            LmsAnnouncementQueryService queryService, LmsAnnouncementManagementService managementService) {
        this.queryService = queryService;
        this.managementService = managementService;
    }

    @GetMapping("/courses/{lmsCourseId}/announcements")
    @PreAuthorize(STUDENT_VIEW)
    @Operation(
            summary = "Get the published announcements of an accessible LMS course",
            description = "Returns the course's published announcements, newest published first, if the signed-in student "
                    + "may currently use the course. Drafts and archived announcements are never included.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The published announcements. The list may be empty.")
    public List<LmsCourseAnnouncementResponse> studentAnnouncements(
            @PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return queryService.getPublishedAnnouncements(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }

    @GetMapping("/faculty/courses/{lmsCourseId}/announcements")
    @PreAuthorize(FACULTY_VIEW)
    @Operation(
            summary = "Get the announcements of a course the faculty member teaches",
            description = "Returns the draft and published announcements, newest created first. Archived ones are kept "
                    + "but not listed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The announcements. The list may be empty.")
    public List<LmsFacultyAnnouncementResponse> facultyAnnouncements(
            @PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return queryService.getManagedAnnouncements(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }

    @PostMapping("/faculty/courses/{lmsCourseId}/announcements")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add an announcement",
            description = "Adds a draft announcement to the course. Students see it once it is published.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new announcement.")
    @ApiResponse(responseCode = "400", description = "The title or message is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyAnnouncementResponse create(
            @PathVariable Long lmsCourseId,
            @Valid @RequestBody LmsAnnouncementRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyAnnouncementResponse.of(managementService.create(
                AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request.title(), request.message()));
    }

    @PutMapping("/faculty/announcements/{announcementId}")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Edit an announcement",
            description = "Changes the title and message of a draft or published announcement. A published one stays "
                    + "published, keeps its publication time, and students see the new text at once.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed announcement.")
    @ApiResponse(responseCode = "400", description = "The title or message is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The announcement is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyAnnouncementResponse update(
            @PathVariable Long announcementId,
            @Valid @RequestBody LmsAnnouncementRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyAnnouncementResponse.of(managementService.update(
                AuthenticatedAccount.idFrom(accessToken), announcementId, request.title(), request.message()));
    }

    @PostMapping("/faculty/announcements/{announcementId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Publish an announcement",
            description = "Shows the announcement to the course's students and records when that first happened. "
                    + "Publishing a published announcement changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The published announcement.")
    @ApiResponse(responseCode = "409", description = "The announcement is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyAnnouncementResponse publish(@PathVariable Long announcementId, @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyAnnouncementResponse.of(managementService.publish(AuthenticatedAccount.idFrom(accessToken), announcementId));
    }

    @PostMapping("/faculty/announcements/{announcementId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Archive an announcement",
            description = "Hides the announcement from students and from the faculty list, and keeps it. Archiving an "
                    + "archived announcement changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The archived announcement.")
    public LmsFacultyAnnouncementResponse archive(@PathVariable Long announcementId, @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyAnnouncementResponse.of(managementService.archive(AuthenticatedAccount.idFrom(accessToken), announcementId));
    }
}
