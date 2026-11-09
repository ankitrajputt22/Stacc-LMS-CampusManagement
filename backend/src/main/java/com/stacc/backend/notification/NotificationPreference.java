package com.stacc.backend.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One person's delivery choice for one category. Without a row, the Inbox is on and nothing is emailed. */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private NotificationCategory category;

    @Column(name = "in_app", nullable = false)
    private boolean inApp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailDelivery email;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
        // Required by JPA.
    }

    public NotificationPreference(Long userAccountId, NotificationCategory category) {
        this.userAccountId = userAccountId;
        this.category = category;
        this.inApp = true;
        this.email = EmailDelivery.OFF;
    }

    public void choose(boolean inApp, EmailDelivery email, Instant at) {
        this.inApp = inApp;
        this.email = email;
        this.updatedAt = at;
    }

    public Long getUserAccountId() { return userAccountId; }
    public NotificationCategory getCategory() { return category; }
    public boolean isInApp() { return inApp; }
    public EmailDelivery getEmail() { return email; }
}
