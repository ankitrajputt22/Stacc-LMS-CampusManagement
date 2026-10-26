package com.stacc.backend.campus.api;

import com.stacc.backend.campus.notices.CampusNoticeInput;
import com.stacc.backend.campus.notices.CampusNoticeQueryService;
import com.stacc.backend.campus.notices.CampusNoticeResponses.AdminCampusNoticePage;
import com.stacc.backend.campus.notices.CampusNoticeResponses.AdminCampusNoticeResponse;
import com.stacc.backend.campus.notices.CampusNoticeService;
import com.stacc.backend.campus.notices.CampusNoticeStatus;
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
 * Admins' campus notices: drafts, corrections, publishing, and archiving. Every route needs the admin role and
 * CAMPUS_NOTICE_MANAGE. There is no delete, restore, or scheduling.
 */
@RestController
@RequestMapping("/api/campus/admin/notices")
@Tag(name = "Campus admin")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class CampusAdminNoticeController {

    private static final String NOT_FOUND = "The notice does not exist.";

    private final CampusNoticeService notices;
    private final CampusNoticeQueryService queries;

    public CampusAdminNoticeController(CampusNoticeService notices, CampusNoticeQueryService queries) {
        this.notices = notices;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize(CampusAccess.MANAGE_NOTICES)
    @Operation(summary = "List campus notices for admins",
            description = "One page of every notice, or those in one status, the most recently created first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of notices. The list may be empty.")
    @ApiResponse(responseCode = "400", description = "The status is not valid, the page is below 0, or the size is not between 1 and 100.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCampusNoticePage list(
            @Parameter(description = "DRAFT, PUBLISHED, or ARCHIVED.") @RequestParam(required = false) CampusNoticeStatus status,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return queries.adminNotices(status, page, size);
    }

    @GetMapping("/{noticeId}")
    @PreAuthorize(CampusAccess.MANAGE_NOTICES)
    @Operation(summary = "Get one campus notice", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The notice.")
    @ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCampusNoticeResponse notice(@PathVariable Long noticeId) {
        return queries.adminNotice(noticeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CampusAccess.MANAGE_NOTICES)
    @Operation(summary = "Add a campus notice",
            description = "Adds a plain-text notice as a draft, visible once published to at least one of students, faculty, and admins. Nobody is notified.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new draft.")
    @ApiResponse(responseCode = "400", description = "A field is missing or too long, or no role is selected.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCampusNoticeResponse create(@Valid @RequestBody CampusNoticeInput input) {
        return notices.create(input);
    }

    @PutMapping("/{noticeId}")
    @PreAuthorize(CampusAccess.MANAGE_NOTICES)
    @Operation(summary = "Edit a campus notice",
            description = "Corrects a draft or published notice. A published notice stays published, keeps its publication time, and notifies nobody again.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The notice.")
    @ApiResponse(responseCode = "400", description = "A field is missing or too long, or no role is selected.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The notice is archived.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCampusNoticeResponse update(@PathVariable Long noticeId, @Valid @RequestBody CampusNoticeInput input) {
        return notices.update(noticeId, input);
    }

    @PostMapping("/{noticeId}/publish")
    @PreAuthorize(CampusAccess.MANAGE_NOTICES)
    @Operation(summary = "Publish a campus notice",
            description = "Publishes a draft: it appears in the feed of the selected roles, and their active accounts are told once in the Inbox. Publishing a published notice changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The notice, now published.")
    @ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The notice is archived.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCampusNoticeResponse publish(@PathVariable Long noticeId) {
        return notices.publish(noticeId);
    }

    @PostMapping("/{noticeId}/archive")
    @PreAuthorize(CampusAccess.MANAGE_NOTICES)
    @Operation(summary = "Archive a campus notice",
            description = "Archives a draft or published notice for good: it leaves the feed and can no longer be edited or published. Archiving an archived notice changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The notice, now archived.")
    @ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCampusNoticeResponse archive(@PathVariable Long noticeId) {
        return notices.archive(noticeId);
    }
}
