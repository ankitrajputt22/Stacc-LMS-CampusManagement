package com.stacc.backend.hostel;

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

/**
 * A resident's request to move to another bed, or a proposed swap of beds with another resident. Its status is the
 * business outcome only (open, completed, cancelled, declined by the partner); the warden's decision stays with the
 * approval engine. Nothing is deleted.
 */
@Entity
@Table(name = "hostel_room_changes")
public class HostelRoomChange {

    public static final int MAX_REASON_LENGTH = 1000;
    public static final int MAX_PREFERENCE_LENGTH = 300;

    public enum Kind {
        MOVE,
        SWAP
    }

    public enum Status {
        OPEN,
        COMPLETED,
        CANCELLED,
        DECLINED
    }

    public enum PartnerResponse {
        PENDING,
        ACCEPTED,
        DECLINED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Kind kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_allocation_id", nullable = false, updatable = false)
    private HostelAllocation requesterAllocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_allocation_id", updatable = false)
    private HostelAllocation partnerAllocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_response", length = 10)
    private PartnerResponse partnerResponse;

    @Column(nullable = false, updatable = false, length = MAX_REASON_LENGTH)
    private String reason;

    @Column(updatable = false, length = MAX_PREFERENCE_LENGTH)
    private String preference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;

    @Column(name = "approval_request_id", unique = true)
    private Long approvalRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_account_id")
    private UserAccount completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HostelRoomChange() {
        // Required by JPA.
    }

    private HostelRoomChange(Kind kind, HostelAllocation requester, HostelAllocation partner, String reason, String preference,
            Instant now) {
        this.kind = kind;
        this.requesterAllocation = Require.notNull(requester, "requesterAllocation");
        this.partnerAllocation = partner;
        this.partnerResponse = partner == null ? null : PartnerResponse.PENDING;
        this.reason = Require.text(reason, "Reason", MAX_REASON_LENGTH);
        this.preference = Require.optionalText(preference, "Preference", MAX_PREFERENCE_LENGTH);
        this.createdAt = Require.notNull(now, "now");
    }

    /** A request to move to another bed, with an optional wish (only a wish: the hostel office chooses the bed). */
    public static HostelRoomChange move(HostelAllocation requester, String reason, String preference, Instant now) {
        return new HostelRoomChange(Kind.MOVE, requester, null, reason, preference, now);
    }

    /** A proposed swap of beds with another resident, who must accept before the warden decides. */
    public static HostelRoomChange swap(HostelAllocation requester, HostelAllocation partner, String reason, Instant now) {
        return new HostelRoomChange(Kind.SWAP, requester, Require.notNull(partner, "partnerAllocation"), reason, null, now);
    }

    public void linkApproval(Long approvalRequestId) {
        if (this.approvalRequestId != null) {
            throw new IllegalStateException("The room change already has an approval request");
        }
        this.approvalRequestId = Require.notNull(approvalRequestId, "approvalRequestId");
    }

    /** The partner's answer to a proposed swap, once; declining ends the change. */
    public void answer(boolean accept) {
        if (kind != Kind.SWAP || partnerResponse != PartnerResponse.PENDING || status != Status.OPEN) {
            throw new IllegalStateException("This swap is no longer waiting for an answer.");
        }
        partnerResponse = accept ? PartnerResponse.ACCEPTED : PartnerResponse.DECLINED;
        if (!accept) {
            status = Status.DECLINED;
        }
    }

    public void cancel() {
        if (status != Status.OPEN) {
            throw new IllegalStateException("Only an open room change can be cancelled.");
        }
        status = Status.CANCELLED;
    }

    public void complete(UserAccount by, Instant at) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("Only an open room change can be completed.");
        }
        status = Status.COMPLETED;
        completedBy = Require.notNull(by, "completedBy");
        completedAt = Require.notNull(at, "at");
    }

    public Long getId() { return id; }
    public Kind getKind() { return kind; }
    public HostelAllocation getRequesterAllocation() { return requesterAllocation; }
    public HostelAllocation getPartnerAllocation() { return partnerAllocation; }
    public PartnerResponse getPartnerResponse() { return partnerResponse; }
    public String getReason() { return reason; }
    public String getPreference() { return preference; }
    public Status getStatus() { return status; }
    public Long getApprovalRequestId() { return approvalRequestId; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
