package com.stacc.backend.hostel;

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

/** A warden's assignment to a hostel, or to one block of it. Ending it keeps it as history; it is never deleted. */
@Entity
@Table(name = "hostel_wardens")
public class HostelWarden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hostel_id", nullable = false, updatable = false)
    private Hostel hostel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, updatable = false)
    private UserAccount warden;

    @Column(length = 50, updatable = false)
    private String block;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected HostelWarden() {
        // Required by JPA.
    }

    public HostelWarden(Hostel hostel, UserAccount warden, String block, Instant at) {
        this.hostel = Require.notNull(hostel, "hostel");
        this.warden = Require.notNull(warden, "warden");
        this.block = Require.optionalText(block, "block", 50);
        this.assignedAt = Require.notNull(at, "at");
    }

    public void end(Instant at) {
        if (endedAt == null) {
            endedAt = at;
        }
    }

    public boolean isActive() { return endedAt == null; }
    public Long getId() { return id; }
    public Hostel getHostel() { return hostel; }
    public UserAccount getWarden() { return warden; }
    public String getBlock() { return block; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getEndedAt() { return endedAt; }
}
