package com.stacc.backend.auth.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @Column(length = 120)
    private String device;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 30)
    private SessionEndReason endReason;

    protected UserSession() {
    }

    public UserSession(Long userAccountId, String device, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.userAccountId = userAccountId;
        this.device = device;
        this.createdAt = now;
        this.lastSeenAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isOpenAt(Instant now, java.time.Duration idleTimeout) {
        return endedAt == null && now.isBefore(expiresAt) && now.isBefore(lastSeenAt.plus(idleTimeout));
    }

    public void end(Instant now, SessionEndReason reason) {
        if (endedAt == null) {
            endedAt = now;
            endReason = reason;
        }
    }

    public String getId() {
        return id;
    }

    public Long getUserAccountId() {
        return userAccountId;
    }

    public String getDevice() {
        return device;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public SessionEndReason getEndReason() {
        return endReason;
    }
}
