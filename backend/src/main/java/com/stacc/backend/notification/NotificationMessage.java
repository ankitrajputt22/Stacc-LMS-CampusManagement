package com.stacc.backend.notification;

public record NotificationMessage(NotificationType type, String title, String message,
        NotificationSourceType sourceType, Long sourceId, String actionPath) {
}
