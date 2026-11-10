package com.stacc.backend.hostel.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.hostel.notice.HostelNotice;
import com.stacc.backend.hostel.notice.HostelNoticeService;
import com.stacc.backend.hostel.notice.HostelNoticeService.Manager;
import com.stacc.backend.hostel.notice.HostelNoticeService.NoticeView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hostel notices: the hostel office and wardens manage them (wardens only for their own hostels); residents read those
 * of their current hostel and block.
 */
@RestController
@Tag(name = "Hostel notices")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HostelNoticeController {

    private static final String MANAGE = "hasAnyRole('ADMIN', 'WARDEN') and hasAuthority('HOSTEL_NOTICE_MANAGE')";
    private static final String PATH = "/api/v1/hostel/notices/manage";

    private final HostelNoticeService service;

    public HostelNoticeController(HostelNoticeService service) {
        this.service = service;
    }

    public record NoticeRequest(@NotNull Long hostelId, @Size(max = 50) String block,
            @NotBlank @Size(max = HostelNotice.MAX_TITLE_LENGTH) String title,
            @NotBlank @Size(max = HostelNotice.MAX_MESSAGE_LENGTH) String message, boolean emergency) {
    }

    public record NoticeEditRequest(@NotBlank @Size(max = HostelNotice.MAX_TITLE_LENGTH) String title,
            @NotBlank @Size(max = HostelNotice.MAX_MESSAGE_LENGTH) String message, boolean emergency) {
    }

    // The hostel office acts for every hostel only with the ADMIN role in the token; a warden only for their own.
    private static Manager manager(Jwt token, Authentication authentication) {
        boolean office = authentication.getAuthorities().stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new Manager(AuthenticatedAccount.idFrom(token), office);
    }

    @GetMapping(PATH)
    @PreAuthorize(MANAGE)
    @Operation(summary = "Notices of a hostel", description = "Drafts, published, and archived, newest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "404", description = "The hostel is not one the account manages.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<NoticeView> forHostel(@RequestParam Long hostelId, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        return service.forHostel(manager(token, authentication), hostelId);
    }

    @PostMapping(PATH)
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a hostel notice", description = "A draft for the hostel, or one block of it.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public NoticeView add(@Valid @RequestBody NoticeRequest request, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        return service.add(manager(token, authentication), request.hostelId(), request.block(), request.title(), request.message(),
                request.emergency());
    }

    @PutMapping(PATH + "/{noticeId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change a hostel notice", description = "A published notice can be corrected but stays an emergency or not.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "The notice is archived, or the emergency flag of a published one would change.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public NoticeView edit(@PathVariable Long noticeId, @Valid @RequestBody NoticeEditRequest request, @AuthenticationPrincipal Jwt token,
            Authentication authentication) {
        return service.edit(manager(token, authentication), noticeId, request.title(), request.message(), request.emergency());
    }

    @PostMapping(PATH + "/{noticeId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Publish a hostel notice",
            description = "Residents it reaches see it on their Hostel page; an emergency notice also tells each of them in the Inbox, once.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public NoticeView publish(@PathVariable Long noticeId, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        return service.publish(manager(token, authentication), noticeId);
    }

    @PostMapping(PATH + "/{noticeId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Archive a hostel notice", description = "It is kept but no longer shown or changed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public NoticeView archive(@PathVariable Long noticeId, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        return service.archive(manager(token, authentication), noticeId);
    }

    @GetMapping("/api/v1/hostel/student/notices")
    @PreAuthorize(HostelAccess.VIEW_OWN)
    @Operation(summary = "Notices of my hostel", description = "Published notices of the student's current hostel that reach their block, emergencies first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<NoticeView> mine(@AuthenticationPrincipal Jwt token) {
        return service.forResident(AuthenticatedAccount.idFrom(token));
    }
}
