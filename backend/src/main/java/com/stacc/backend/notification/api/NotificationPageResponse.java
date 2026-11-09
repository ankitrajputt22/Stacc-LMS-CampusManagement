package com.stacc.backend.notification.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** One page of the Inbox, newest first. */
public record NotificationPageResponse(
        List<NotificationInboxItem> items,
        @Schema(description = "The page number, from 0.", example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "37") long totalElements,
        @Schema(example = "2") int totalPages) {
}
