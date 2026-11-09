package com.stacc.backend.calendar.personal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A private calendar subscription link. Only the SHA-256 fingerprint of its secret is kept. */
@Entity
@Table(name = "calendar_feed_links")
public class CalendarFeedLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @Column(name = "token_fingerprint", nullable = false, updatable = false, length = 64)
    private String tokenFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected CalendarFeedLink() {
        // Required by JPA.
    }

    public CalendarFeedLink(Long userAccountId, String tokenFingerprint, Instant createdAt) {
        this.userAccountId = userAccountId;
        this.tokenFingerprint = tokenFingerprint;
        this.createdAt = createdAt;
    }

    public void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }

    public Long getUserAccountId() { return userAccountId; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isActive() { return revokedAt == null; }
}
