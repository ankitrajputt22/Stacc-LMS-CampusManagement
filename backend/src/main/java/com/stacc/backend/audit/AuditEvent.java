package com.stacc.backend.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One recorded action. Every column is fixed once written: there are no setters, and nothing updates or deletes. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    public static final int MAX_DETAILS = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_account_id", updatable = false)
    private Long actorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60, updatable = false)
    private AuditAction action;

    @Column(name = "target_type", nullable = false, length = 40, updatable = false)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    @Column(length = MAX_DETAILS, updatable = false)
    private String details;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    protected AuditEvent() {
        // Required by JPA.
    }

    AuditEvent(Instant occurredAt, Long actorAccountId, AuditAction action, String targetType, Long targetId,
            String details, String requestId) {
        this.occurredAt = occurredAt;
        this.actorAccountId = actorAccountId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
        this.requestId = requestId;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getActorAccountId() {
        return actorAccountId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getDetails() {
        return details;
    }

    public String getRequestId() {
        return requestId;
    }
}
