package com.stacc.backend.notification.api;

import java.time.Instant;

import com.stacc.backend.notification.Notification;
import com.stacc.backend.notification.NotificationRecipient;
import io.swagger.v3.oas.annotations.media.Schema;

/** One notification in the signed-in account's Inbox. No account ID or other recipient is ever included. */
public record NotificationInboxItem(
        @Schema(description = "The ID of this Inbox row, used to mark it read or unread.", example = "310")
        Long notificationRecipientId,

        @Schema(example = "57")
        Long notificationId,

        @Schema(allowableValues = {"ASSIGNMENT_PUBLISHED", "ASSIGNMENT_GRADED", "ACADEMIC_CALENDAR_PUBLISHED",
                "ACADEMIC_CALENDAR_CANCELLED", "ATTENDANCE_UPDATED", "RESULT_PUBLISHED", "RESULT_UPDATED"},
                example = "ASSIGNMENT_PUBLISHED")
        String type,

        @Schema(example = "New assignment published")
        String title,

        @Schema(description = "Plain text, never HTML.", example = "Linked list implementation was published in CS301 Data Structures.")
        String message,

        @Schema(description = "What the notification is about, or null. A reference only: it grants no access.",
                allowableValues = {"ASSIGNMENT", "SUBMISSION_GRADE", "ACADEMIC_CALENDAR_EVENT", "ATTENDANCE_RECORD",
                        "COURSE_RESULT"}, nullable = true, example = "ASSIGNMENT")
        String sourceType,

        @Schema(nullable = true, example = "44")
        Long sourceId,

        @Schema(description = "A path inside the application to look at it, or null. The page there checks access as "
                + "always.", nullable = true, example = "/courses/12/assignments/44")
        String actionPath,

        @Schema(description = "When this account marked it read, or null while it is unread.", nullable = true,
                example = "2026-10-21T08:15:00Z")
        Instant readAt,

        @Schema(example = "2026-10-20T10:00:00Z")
        Instant createdAt) {

    static NotificationInboxItem of(NotificationRecipient recipient) {
        Notification notification = recipient.getNotification();
        return new NotificationInboxItem(recipient.getId(), notification.getId(), notification.getType().name(),
                notification.getTitle(), notification.getMessage(),
                notification.getSourceType() == null ? null : notification.getSourceType().name(),
                notification.getSourceId(), notification.getActionPath(), recipient.getReadAt(),
                notification.getCreatedAt());
    }
}
