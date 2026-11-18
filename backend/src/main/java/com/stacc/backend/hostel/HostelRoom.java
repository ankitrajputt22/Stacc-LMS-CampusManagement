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

@Entity
@Table(name = "hostel_rooms")
public class HostelRoom {

    public static final int MAX_NUMBER_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hostel_id", nullable = false, updatable = false)
    private Hostel hostel;

    @Column(name = "room_number", nullable = false, length = MAX_NUMBER_LENGTH)
    private String roomNumber;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 50)
    private String block;

    @Column(name = "floor_number")
    private Integer floorNumber;

    @Column(name = "room_type", length = 50)
    private String roomType;

    @Column(length = 500)
    private String notes;

    public void describe(String block, Integer floorNumber, String roomType, String notes) {
        if (floorNumber != null && (floorNumber < -5 || floorNumber > 200)) {
            throw new IllegalArgumentException("the floor must be between -5 and 200");
        }
        this.block = Require.optionalText(block, "block", 50);
        this.floorNumber = floorNumber;
        this.roomType = Require.optionalText(roomType, "room type", 50);
        this.notes = Require.optionalText(notes, "notes", 500);
    }

    public String getBlock() { return block; }
    public Integer getFloorNumber() { return floorNumber; }
    public String getRoomType() { return roomType; }
    public String getNotes() { return notes; }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HostelRoom() {
    }

    public HostelRoom(Hostel hostel, String roomNumber) {
        this.hostel = Require.notNull(hostel, "hostel");
        update(roomNumber, true);
    }

    public void update(String roomNumber, boolean active) {
        this.roomNumber = Require.text(roomNumber, "roomNumber", MAX_NUMBER_LENGTH);
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public Hostel getHostel() {
        return hostel;
    }

    public String getRoomNumber() {
        return roomNumber;
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
