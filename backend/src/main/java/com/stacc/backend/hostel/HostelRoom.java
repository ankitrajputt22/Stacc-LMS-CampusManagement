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
 * A room in one hostel. Its capacity is the number of its active beds; it has no capacity field of its own. An inactive
 * room takes no new allocations. Rooms are never deleted.
 */
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HostelRoom() {
        // Required by JPA.
    }

    public HostelRoom(Hostel hostel, String roomNumber) {
        this.hostel = Require.notNull(hostel, "hostel");
        update(roomNumber, true);
    }

    /** Sets the number and active flag. Whether the number may still change is checked by the caller. */
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
