package com.stacc.backend.notification.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** How many of the signed-in account's notifications are unread. */
public record UnreadNotificationCount(@Schema(example = "3") long unreadCount) {
}
