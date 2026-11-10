package com.stacc.backend.hostel.rollcall;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.hostel.HostelAllocation;
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
 * One resident on a roll call sheet: UNMARKED until the warden marks them PRESENT, ABSENT, or OUT. A resident whose
 * last gate movement was going out starts as OUT; nobody starts as ABSENT.
 */
@Entity
@Table(name = "hostel_roll_call_entries")
public class HostelRollCallEntry {

    public enum Status {
        UNMARKED,
        PRESENT,
        ABSENT,
        OUT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roll_call_id", nullable = false, updatable = false)
    private HostelRollCall rollCall;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id", nullable = false, updatable = false)
    private HostelAllocation allocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "prefilled_out", nullable = false, updatable = false)
    private boolean prefilledOut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by_account_id")
    private UserAccount markedBy;

    @Column(name = "marked_at")
    private Instant markedAt;

    protected HostelRollCallEntry() {
        // Required by JPA.
    }

    public HostelRollCallEntry(HostelRollCall rollCall, HostelAllocation allocation, boolean outside) {
        this.rollCall = Require.notNull(rollCall, "rollCall");
        this.allocation = Require.notNull(allocation, "allocation");
        this.prefilledOut = outside;
        this.status = outside ? Status.OUT : Status.UNMARKED;
    }

    /** Marks the resident; a sheet only takes real marks, never back to UNMARKED. */
    public void mark(Status marked, UserAccount by, Instant at) {
        if (marked == null || marked == Status.UNMARKED) {
            throw new IllegalArgumentException("Mark each resident present, absent, or out.");
        }
        this.status = marked;
        this.markedBy = Require.notNull(by, "markedBy");
        this.markedAt = Require.notNull(at, "at");
    }

    public Long getId() { return id; }
    public HostelRollCall getRollCall() { return rollCall; }
    public HostelAllocation getAllocation() { return allocation; }
    public Status getStatus() { return status; }
    public boolean isPrefilledOut() { return prefilledOut; }
    public Instant getMarkedAt() { return markedAt; }
}
