package com.stacc.backend.notification;

/**
 * The content of one notification, as a feature service describes it: what happened, in plain text, the record it is
 * about, and where to look at it. It never names its recipients.
 *
 * @param sourceType what it is about, or null together with sourceId
 * @param actionPath a path inside the application, such as /academic/results, or null
 */
public record NotificationMessage(NotificationType type, String title, String message,
        NotificationSourceType sourceType, Long sourceId, String actionPath) {
}
