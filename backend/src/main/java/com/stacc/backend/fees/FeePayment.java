package com.stacc.backend.fees;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

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

@Entity
@Table(name = "fee_payments")
public class FeePayment {

    public enum Mode { UPI, BANK_TRANSFER, CARD, CASH, CHEQUE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_account_id", nullable = false, updatable = false)
    private UserAccount student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_session_id", nullable = false, updatable = false)
    private AcademicSession academicSession;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Mode mode;

    @Column(length = 60, updatable = false)
    private String reference;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    @Column(name = "recorded_by_account_id", nullable = false, updatable = false)
    private Long recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FeePayment() {
    }

    public FeePayment(UserAccount student, AcademicSession academicSession, BigDecimal amount, Mode mode, String reference,
            LocalDate paidOn, Long recordedBy, Instant createdAt) {
        this.student = student;
        this.academicSession = academicSession;
        this.amount = amount;
        this.mode = mode;
        this.reference = reference;
        this.paidOn = paidOn;
        this.recordedBy = recordedBy;
        this.createdAt = createdAt;
    }

    public String receiptNumber() {
        return "RCP-" + createdAt.atZone(ZoneOffset.UTC).getYear() + "-" + String.format("%06d", id);
    }

    public Long getId() {
        return id;
    }

    public UserAccount getStudent() {
        return student;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Mode getMode() {
        return mode;
    }

    public String getReference() {
        return reference;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
