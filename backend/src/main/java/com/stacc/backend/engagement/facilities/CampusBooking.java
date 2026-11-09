package com.stacc.backend.engagement.facilities;

import java.time.Instant;

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

/** A booking of a facility. For a facility that needs approval, its state comes from the approval engine. */
@Entity
@Table(name = "campus_bookings")
public class CampusBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false, updatable = false)
    private CampusFacility facility;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, updatable = false)
    private UserAccount account;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Column(nullable = false, updatable = false, length = 500)
    private String purpose;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CampusBooking() {
        // Required by JPA.
    }

    public CampusBooking(CampusFacility facility, UserAccount account, Instant startsAt, Instant endsAt, String purpose, Instant now) {
        this.facility = facility;
        this.account = account;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.purpose = Require.text(purpose, "purpose", 500);
        this.createdAt = now;
    }

    public void linkApproval(Long approvalRequestId) {
        this.approvalRequestId = approvalRequestId;
    }

    public void cancel(Instant at) {
        if (cancelledAt == null) {
            cancelledAt = at;
        }
    }

    public Long getId() { return id; }
    public CampusFacility getFacility() { return facility; }
    public UserAccount getAccount() { return account; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getPurpose() { return purpose; }
    public Long getApprovalRequestId() { return approvalRequestId; }
    public Instant getCancelledAt() { return cancelledAt; }
}
