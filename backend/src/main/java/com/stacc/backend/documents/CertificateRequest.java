package com.stacc.backend.documents;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A request for a certificate. The college issues it into the requester's vault, or refuses it with a note. */
@Entity
@Table(name = "certificate_requests")
public class CertificateRequest {

    public enum Type { BONAFIDE, TRANSCRIPT, CHARACTER, COURSE_COMPLETION, OTHER }

    public enum Status { REQUESTED, ISSUED, REFUSED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_account_id", nullable = false, updatable = false)
    private UserAccount requester;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type", nullable = false, updatable = false, length = 30)
    private Type type;

    @Column(nullable = false, updatable = false, length = 500)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REQUESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_account_id")
    private UserAccount decidedBy;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected CertificateRequest() {
        // Required by JPA.
    }

    public CertificateRequest(UserAccount requester, Type type, String purpose, Instant now) {
        this.requester = requester;
        this.type = Require.notNull(type, "type");
        this.purpose = Require.text(purpose, "purpose", 500);
        this.createdAt = now;
    }

    public void decide(Status outcome, UserAccount by, String note, Instant at) {
        if (status != Status.REQUESTED) {
            throw new IllegalStateException("This request was already finished");
        }
        this.status = outcome;
        this.decidedBy = by;
        this.decisionNote = Require.optionalText(note, "note", 500);
        this.decidedAt = at;
    }

    public Long getId() { return id; }
    public UserAccount getRequester() { return requester; }
    public Type getType() { return type; }
    public String getPurpose() { return purpose; }
    public Status getStatus() { return status; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
