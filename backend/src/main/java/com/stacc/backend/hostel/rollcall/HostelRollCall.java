package com.stacc.backend.hostel.rollcall;

import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.hostel.Hostel;
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

/** One night's roll call sheet of a hostel, or of one block of it. Finalized once; never deleted or reopened. */
@Entity
@Table(name = "hostel_roll_calls")
public class HostelRollCall {

    public enum Status {
        OPEN,
        FINALIZED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hostel_id", nullable = false, updatable = false)
    private Hostel hostel;

    @Column(updatable = false, length = 50)
    private String block;

    @Column(name = "roll_date", nullable = false, updatable = false)
    private LocalDate rollDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_account_id", nullable = false, updatable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    protected HostelRollCall() {
        // Required by JPA.
    }

    public HostelRollCall(Hostel hostel, String block, LocalDate rollDate, UserAccount createdBy, Instant now) {
        this.hostel = Require.notNull(hostel, "hostel");
        this.block = Require.optionalText(block, "Block", 50);
        this.rollDate = Require.notNull(rollDate, "Date");
        this.createdBy = Require.notNull(createdBy, "createdBy");
        this.createdAt = Require.notNull(now, "now");
    }

    public void finalizeAt(Instant at) {
        if (status == Status.FINALIZED) {
            return;
        }
        status = Status.FINALIZED;
        finalizedAt = Require.notNull(at, "at");
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public Long getId() { return id; }
    public Hostel getHostel() { return hostel; }
    public String getBlock() { return block; }
    public LocalDate getRollDate() { return rollDate; }
    public Status getStatus() { return status; }
    public UserAccount getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinalizedAt() { return finalizedAt; }
}
