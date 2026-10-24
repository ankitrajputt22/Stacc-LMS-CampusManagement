package com.stacc.backend.notification.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** The result of marking the whole Inbox read. */
public record NotificationReadAllResponse(
        @Schema(description = "How many unread notifications were marked read now.", example = "3") int markedRead,
        @Schema(description = "Always 0 afterwards.", example = "0") long unreadCount) {
}
