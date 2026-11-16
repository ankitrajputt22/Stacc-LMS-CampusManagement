package com.stacc.backend.fees;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
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

/** A scholarship applied for, or a refund asked for, by a student, decided once by the accounts office. */
@Entity
@Table(name = "fee_adjustments")
public class FeeAdjustment {

    public enum Kind { SCHOLARSHIP, REFUND }

    public enum Status { SUBMITTED, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Kind kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_account_id", nullable = false, updatable = false)
    private UserAccount student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_session_id", nullable = false, updatable = false)
    private AcademicSession academicSession;

    @Column(nullable = false, length = 150, updatable = false)
    private String title;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 1000, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUBMITTED;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @Column(name = "decided_by_account_id")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FeeAdjustment() {
    }

    public FeeAdjustment(Kind kind, UserAccount student, AcademicSession academicSession, String title, BigDecimal amount, String reason,
            Instant createdAt) {
        this.kind = kind;
        this.student = student;
        this.academicSession = academicSession;
        this.title = title;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public void decide(boolean approve, Long by, String comment, Instant at) {
        if (status != Status.SUBMITTED) {
            throw new IllegalStateException("Already decided.");
        }
        status = approve ? Status.APPROVED : Status.REJECTED;
        decidedBy = by;
        decisionComment = comment;
        decidedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public UserAccount getStudent() {
        return student;
    }

    public AcademicSession getAcademicSession() {
        return academicSession;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
