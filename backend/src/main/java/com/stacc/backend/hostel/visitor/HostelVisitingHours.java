package com.stacc.backend.hostel.visitor;

import java.time.Instant;
import java.time.LocalTime;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** When a hostel takes visitors each day, in the college's time zone. A hostel without hours takes no visitors. */
@Entity
@Table(name = "hostel_visiting_hours")
public class HostelVisitingHours {

    @Id
    @Column(name = "hostel_id")
    private Long hostelId;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HostelVisitingHours() {
        // Required by JPA.
    }

    public HostelVisitingHours(Long hostelId, LocalTime opensAt, LocalTime closesAt, Instant now) {
        this.hostelId = Require.notNull(hostelId, "hostelId");
        change(opensAt, closesAt, now);
    }

    public void change(LocalTime opens, LocalTime closes, Instant now) {
        Require.notNull(opens, "Opening time");
        Require.notNull(closes, "Closing time");
        if (!opens.isBefore(closes)) {
            throw new IllegalArgumentException("Visiting hours must open before they close.");
        }
        this.opensAt = opens;
        this.closesAt = closes;
        this.updatedAt = Require.notNull(now, "now");
    }

    public boolean contains(LocalTime time) {
        return !time.isBefore(opensAt) && time.isBefore(closesAt);
    }

    public Long getHostelId() { return hostelId; }
    public LocalTime getOpensAt() { return opensAt; }
    public LocalTime getClosesAt() { return closesAt; }
}
