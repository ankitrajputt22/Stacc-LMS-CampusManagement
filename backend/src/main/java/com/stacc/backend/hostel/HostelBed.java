package com.stacc.backend.hostel;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One bed in a room: the unit a student is allocated to. It has no occupied flag: a bed is occupied exactly when it has
 * an ACTIVE allocation. An inactive bed takes no new allocations. Beds are never deleted.
 */
@Entity
@Table(name = "hostel_beds")
public class HostelBed {

    public static final int MAX_NUMBER_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private HostelRoom room;

    @Column(name = "bed_number", nullable = false, length = MAX_NUMBER_LENGTH)
    private String bedNumber;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HostelBed() {
        // Required by JPA.
    }

    public HostelBed(HostelRoom room, String bedNumber) {
        this.room = Require.notNull(room, "room");
        update(bedNumber, true);
    }

    /** Sets the number and active flag. Whether the number may still change is checked by the caller. */
    public void update(String bedNumber, boolean active) {
        this.bedNumber = Require.text(bedNumber, "bedNumber", MAX_NUMBER_LENGTH);
        this.active = active;
    }

    /** Whether a new allocation may use this bed: the bed, its room, and its hostel are all active. */
    public boolean isAllocatable() {
        return active && room.isActive() && room.getHostel().isActive();
    }

    public Long getId() {
        return id;
    }

    public HostelRoom getRoom() {
        return room;
    }

    public String getBedNumber() {
        return bedNumber;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
