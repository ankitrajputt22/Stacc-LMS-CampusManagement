/**
 * In-app notifications and the Inbox: shared infrastructure that feature services use to tell users about real
 * changes, such as a published assignment or a corrected result. A {@link com.stacc.backend.notification.Notification}
 * holds the content once; each recipient's delivery and read state is a
 * {@link com.stacc.backend.notification.NotificationRecipient}. Notifications are created only inside the backend,
 * never through a public endpoint. The feature that triggered one stays the source of truth. There is no email, push,
 * or real-time delivery. See {@code docs/notifications-inbox.md}.
 */
package com.stacc.backend.notification;
