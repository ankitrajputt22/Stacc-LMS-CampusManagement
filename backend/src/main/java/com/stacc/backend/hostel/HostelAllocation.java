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

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "check_in_note", length = 500)
    private String checkInNote;

    @Column(name = "vacate_condition_note", length = 500)
    private String vacateConditionNote;

    public void checkIn(Instant at, String note) {
        if (status != HostelAllocationStatus.ACTIVE) {
            throw new IllegalStateException("Only an active allocation can be checked in");
        }
        if (checkedInAt != null) {
            return;
        }
        this.checkedInAt = Require.notNull(at, "at").truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        this.checkInNote = Require.optionalText(note, "note", 500);
    }

    public void conditionOnVacating(String note) {
        if (vacateConditionNote == null) {
            this.vacateConditionNote = Require.optionalText(note, "condition note", 500);
        }
    }

    public Instant getCheckedInAt() { return checkedInAt; }
    public String getCheckInNote() { return checkInNote; }
    public String getVacateConditionNote() { return vacateConditionNote; }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HostelAllocation() {
    }

    public HostelAllocation(HostelApplication hostelApplication, HostelBed bed, UserAccount allocatedBy, Instant at) {
        this.hostelApplication = Require.notNull(hostelApplication, "hostelApplication");
        this.bed = Require.notNull(bed, "bed");
        this.allocatedBy = Require.notNull(allocatedBy, "allocatedBy");
        this.allocatedAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isActive() {
        return status == HostelAllocationStatus.ACTIVE;
    }

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
