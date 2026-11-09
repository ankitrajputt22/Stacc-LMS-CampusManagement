package com.stacc.backend.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A notification waiting for one person's daily digest. */
@Entity
@Table(name = "notification_digest_items")
public class NotificationDigestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false, updatable = false)
    private Notification notification;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationDigestItem() {
        // Required by JPA.
    }

    public NotificationDigestItem(Long userAccountId, Notification notification, Instant createdAt) {
        this.userAccountId = userAccountId;
        this.notification = notification;
        this.createdAt = createdAt;
    }

    public void sent(Instant at) {
        this.sentAt = at;
    }

    public Long getUserAccountId() { return userAccountId; }
    public Notification getNotification() { return notification; }
}
