package com.stacc.backend.pass;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One issued pass. The code itself is never stored. */
@Entity
@Table(name = "passes")
public class Pass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private PassPurpose purpose;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "holder_user_account_id", nullable = false, updatable = false)
    private Long holderAccountId;

    @Column(name = "code_fingerprint", nullable = false, updatable = false, length = 64)
    private String codeFingerprint;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private Instant validUntil;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Pass() {
        // Required by JPA.
    }

    Pass(PassPurpose purpose, Long sourceId, Long holderAccountId, String codeFingerprint, Instant validFrom, Instant validUntil, Instant issuedAt) {
        this.purpose = purpose;
        this.sourceId = sourceId;
        this.holderAccountId = holderAccountId;
        this.codeFingerprint = codeFingerprint;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.issuedAt = issuedAt;
    }

    void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }

    /** Valid at this moment: inside its window and not revoked. */
    public boolean validAt(Instant at) {
        return revokedAt == null && !at.isBefore(validFrom) && !at.isAfter(validUntil);
    }

    public Long getId() { return id; }
    public PassPurpose getPurpose() { return purpose; }
    public Long getSourceId() { return sourceId; }
    public Long getHolderAccountId() { return holderAccountId; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public Instant getRevokedAt() { return revokedAt; }
}
