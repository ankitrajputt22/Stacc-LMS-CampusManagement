package com.stacc.backend.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One account's copy of a notification in its Inbox: unread while {@code readAt} is empty, read once it holds when the
 * account first marked it read. Marking it unread empties {@code readAt} again; no history of these changes is kept.
 * An account receives a notification at most once. Nothing here is ever deleted.
 */
@Entity
@Table(
        name = "notification_recipients",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_recipients_notification_account",
                columnNames = {"notification_id", "user_account_id"}))
public class NotificationRecipient {

    // No cascade: deleting neither an account nor a notification ever removes Inbox rows through here.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false, updatable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, updatable = false)
    private UserAccount userAccount;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationRecipient() {
        // Required by JPA.
    }

    public NotificationRecipient(Notification notification, UserAccount userAccount) {
        this.notification = Require.notNull(notification, "notification");
        this.userAccount = Require.notNull(userAccount, "userAccount");
    }

    public Long getId() {
        return id;
    }

    public Notification getNotification() {
        return notification;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** Marks it read at the given time, to the microsecond as stored. Already read keeps its first read time. */
    public void markRead(Instant at) {
        if (readAt == null) {
            this.readAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
        }
    }

    /** Marks it unread again. Already unread changes nothing. */
    public void markUnread() {
        this.readAt = null;
    }
}
