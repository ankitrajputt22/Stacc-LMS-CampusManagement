package com.stacc.backend.engagement.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.groups.GroupActivityService;
import com.stacc.backend.engagement.groups.GroupActivityService.Activity;
import com.stacc.backend.engagement.groups.GroupActivityService.AnnouncementView;
import com.stacc.backend.engagement.groups.GroupActivityService.SessionView;
import com.stacc.backend.engagement.groups.GroupAnnouncement;
import com.stacc.backend.engagement.groups.GroupSession;
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
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sessions and announcements of clubs and sports groups, and the sessions on the signed-in member's calendar. */
@RestController
@RequestMapping("/api/v1/engagement")
@Tag(name = "Campus engagement")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class GroupActivityController {

    private static final String USE = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('CAMPUS_ENGAGEMENT_USE')";
    private static final String MANAGE = "hasRole('ADMIN') and hasAuthority('CAMPUS_ENGAGEMENT_MANAGE')";
    private static final String ANY = "(" + USE + ") or (" + MANAGE + ")";

    private final GroupActivityService service;

    public GroupActivityController(GroupActivityService service) {
        this.service = service;
    }

    public record SessionRequest(@NotNull GroupSession.Kind kind, @NotBlank @Size(max = 200) String title, @Size(max = 200) String location,
            @NotNull Instant startsAt, @NotNull Instant endsAt) {
    }

    public record AnnouncementRequest(@NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = GroupAnnouncement.MAX_MESSAGE_LENGTH) String message) {
    }

    private static boolean office(Authentication authentication) {
        Set<String> held = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        return held.contains("ROLE_ADMIN") && held.contains("CAMPUS_ENGAGEMENT_MANAGE");
    }

    @GetMapping("/groups/{groupId}/activity")
    @PreAuthorize(ANY)
    @Operation(summary = "A group's sessions and announcements", description = "For active members, officers, and the campus office; others get 404.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public Activity activity(@PathVariable Long groupId, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        return service.activity(AuthenticatedAccount.idFrom(token), office(authentication), groupId);
    }

    @PostMapping("/groups/{groupId}/sessions")
    @PreAuthorize(ANY)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Schedule a meeting or practice", description = "By the group's officers or the campus office. It appears on members' calendars.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public SessionView addSession(@PathVariable Long groupId, @Valid @RequestBody SessionRequest request, @AuthenticationPrincipal Jwt token,
            Authentication authentication) {
        return service.addSession(AuthenticatedAccount.idFrom(token), office(authentication), groupId, request.kind(), request.title(),
                request.location(), request.startsAt(), request.endsAt());
    }

    @PostMapping("/sessions/{sessionId}/cancel")
    @PreAuthorize(ANY)
    @Operation(summary = "Cancel a session", description = "It is kept, marked cancelled, and leaves members' calendars.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public SessionView cancelSession(@PathVariable Long sessionId, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        return service.cancelSession(AuthenticatedAccount.idFrom(token), office(authentication), sessionId);
    }

    @PostMapping("/groups/{groupId}/announcements")
    @PreAuthorize(ANY)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a group announcement", description = "By the group's officers or the campus office. Active members are told in their Inbox.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public AnnouncementView announce(@PathVariable Long groupId, @Valid @RequestBody AnnouncementRequest request, @AuthenticationPrincipal Jwt token,
            Authentication authentication) {
        return service.announce(AuthenticatedAccount.idFrom(token), office(authentication), groupId, request.title(), request.message());
    }

    @GetMapping("/me/sessions")
    @PreAuthorize(USE)
    @Operation(summary = "My group sessions", description = "Scheduled sessions of the groups I am an active member of, overlapping the range (at most 62 days).",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "400", description = "The range is missing, reversed, or longer than 62 days.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<SessionView> mySessions(@RequestParam Instant from, @RequestParam Instant to, @AuthenticationPrincipal Jwt token) {
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(62)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a range of at most 62 days.");
        }
        return service.mySessions(AuthenticatedAccount.idFrom(token), from, to);
    }
}
