package com.stacc.backend.hostel.outing;

import java.time.Instant;

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

/** A request to stay away longer on approved leave. Once approved, the leave's end is its requested end. */
@Entity
@Table(name = "hostel_outing_extensions")
public class HostelOutingExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outing_id", nullable = false, updatable = false)
    private HostelOuting outing;

    @Column(name = "requested_until", nullable = false, updatable = false)
    private Instant requestedUntil;

    @Column(nullable = false, updatable = false, length = 1000)
    private String reason;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HostelOutingExtension() {
        // Required by JPA.
    }

    public HostelOutingExtension(HostelOuting outing, Instant requestedUntil, String reason, Instant now) {
        this.outing = Require.notNull(outing, "outing");
        this.requestedUntil = Require.notNull(requestedUntil, "until");
        this.reason = Require.text(reason, "reason", 1000);
        this.createdAt = Require.notNull(now, "now");
    }

    public void linkApproval(Long approvalRequestId) {
        this.approvalRequestId = approvalRequestId;
    }

    public Long getId() { return id; }
    public HostelOuting getOuting() { return outing; }
    public Instant getRequestedUntil() { return requestedUntil; }
    public String getReason() { return reason; }
    public Long getApprovalRequestId() { return approvalRequestId; }
    public Instant getCreatedAt() { return createdAt; }
}
