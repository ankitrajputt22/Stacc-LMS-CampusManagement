package com.stacc.backend.hostel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One approved application's stay in one bed. An ACTIVE allocation is what makes a bed occupied. Vacating keeps the
 * row as history; an application may get a new allocation after an earlier one was vacated. Nothing is deleted.
 */
@Entity
@Table(name = "hostel_allocations")
public class HostelAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hostel_application_id", nullable = false, updatable = false)
    private HostelApplication hostelApplication;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hostel_bed_id", nullable = false, updatable = false)
    private HostelBed bed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HostelAllocationStatus status = HostelAllocationStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocated_by_user_account_id", nullable = false, updatable = false)
    private UserAccount allocatedBy;

    @Column(name = "allocated_at", nullable = false, updatable = false)
    private Instant allocatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vacated_by_user_account_id")
    private UserAccount vacatedBy;

    @Column(name = "vacated_at")
    private Instant vacatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HostelAllocation() {
        // Required by JPA.
    }

    /** A new ACTIVE allocation. Whether it is allowed is checked by the caller, under its locks. */
    public HostelAllocation(HostelApplication hostelApplication, HostelBed bed, UserAccount allocatedBy, Instant at) {
        this.hostelApplication = Require.notNull(hostelApplication, "hostelApplication");
        this.bed = Require.notNull(bed, "bed");
        this.allocatedBy = Require.notNull(allocatedBy, "allocatedBy");
        this.allocatedAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isActive() {
        return status == HostelAllocationStatus.ACTIVE;
    }

    /** Ends the allocation and records who ended it and when. Only an ACTIVE allocation can be vacated. */
    public void vacate(UserAccount actor, Instant at) {
        if (!isActive()) {
            throw new IllegalStateException("Only an active allocation can be vacated");
        }
        this.status = HostelAllocationStatus.VACATED;
        this.vacatedBy = Require.notNull(actor, "actor");
        this.vacatedAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public HostelApplication getHostelApplication() {
        return hostelApplication;
    }

    public HostelBed getBed() {
        return bed;
    }

    public HostelAllocationStatus getStatus() {
        return status;
    }

    public UserAccount getAllocatedBy() {
        return allocatedBy;
    }

    public Instant getAllocatedAt() {
        return allocatedAt;
    }

    public UserAccount getVacatedBy() {
        return vacatedBy;
    }

    public Instant getVacatedAt() {
        return vacatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
