package com.stacc.backend.notification.api;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in account's Inbox. Every route needs a student, faculty, or admin role and {@code NOTIFICATION_VIEW}, and
 * works only on the notifications of the account in the access token: one Inbox per account, whatever roles it holds.
 * Notifications are created inside the backend only, so there is no route to create, send, or delete one.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class NotificationController {

    static final String ACCESS = "hasAnyRole('STUDENT', 'FACULTY', 'ADMIN') and hasAuthority('NOTIFICATION_VIEW')";

    private final NotificationInboxService inbox;

    public NotificationController(NotificationInboxService inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    @PreAuthorize(ACCESS)
    @Operation(
            summary = "List the signed-in account's notifications",
            description = "Returns one page of the account's own notifications, newest first, read and unread, or only unread ones. Loading the Inbox marks nothing read.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of the Inbox. The list may be empty.")
    @ApiResponse(
            responseCode = "400",
            description = "The page is below 0, or the size is not between 1 and 100.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public NotificationPageResponse list(
            @AuthenticationPrincipal Jwt accessToken,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Only notifications that are still unread.") @RequestParam(defaultValue = "false")
            boolean unreadOnly) {
        return inbox.inbox(AuthenticatedAccount.idFrom(accessToken), page, size, unreadOnly);
    }

    @GetMapping("/unread-count")
    @PreAuthorize(ACCESS)
    @Operation(summary = "Count the signed-in account's unread notifications",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The number of unread notifications.")
    public UnreadNotificationCount unreadCount(@AuthenticationPrincipal Jwt accessToken) {
        return inbox.unreadCount(AuthenticatedAccount.idFrom(accessToken));
    }

    @PostMapping("/{notificationRecipientId}/read")
    @PreAuthorize(ACCESS)
    @Operation(
            summary = "Mark a notification read",
            description = "Marks one of the account's own notifications read and records when. Marking it again keeps the first time.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The notification, now read.")
    @ApiResponse(
            responseCode = "404",
            description = "The notification does not exist, or belongs to another account.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public NotificationInboxItem markRead(@AuthenticationPrincipal Jwt accessToken,
            @PathVariable Long notificationRecipientId) {
        return inbox.markRead(AuthenticatedAccount.idFrom(accessToken), notificationRecipientId);
    }

    @PostMapping("/{notificationRecipientId}/unread")
    @PreAuthorize(ACCESS)
    @Operation(
            summary = "Mark a notification unread",
            description = "Marks one of the account's own notifications unread again. Marking it again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The notification, now unread.")
    @ApiResponse(
            responseCode = "404",
            description = "The notification does not exist, or belongs to another account.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public NotificationInboxItem markUnread(@AuthenticationPrincipal Jwt accessToken,
            @PathVariable Long notificationRecipientId) {
        return inbox.markUnread(AuthenticatedAccount.idFrom(accessToken), notificationRecipientId);
    }

    @PostMapping("/read-all")
    @PreAuthorize(ACCESS)
    @Operation(
            summary = "Mark all notifications read",
            description = "Marks every unread notification of the signed-in account read. Other accounts are never touched.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "How many were marked read.")
    public NotificationReadAllResponse markAllRead(@AuthenticationPrincipal Jwt accessToken) {
        return inbox.markAllRead(AuthenticatedAccount.idFrom(accessToken));
    }
}
