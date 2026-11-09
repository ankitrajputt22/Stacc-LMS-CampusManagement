package com.stacc.backend.engagement.facilities;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Locale;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A bookable room, lab, hall, court, or ground with daily opening hours. Made inactive, never deleted. */
@Entity
@Table(name = "campus_facilities")
public class CampusFacility {

    public enum Kind { ROOM, LAB, HALL, COURT, GROUND, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(length = 200)
    private String location;

    private Integer capacity;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Column(name = "needs_approval", nullable = false)
    private boolean needsApproval;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CampusFacility() {
        // Required by JPA.
    }

    public CampusFacility(String code, String name, Kind kind, String location, Integer capacity, LocalTime opensAt,
            LocalTime closesAt, boolean needsApproval) {
        String normalized = Require.text(code, "code", 50).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_-]*")) {
            throw new IllegalArgumentException("the code uses capital letters, digits, dashes, and underscores");
        }
        this.code = normalized;
        update(name, kind, location, capacity, opensAt, closesAt, needsApproval, true);
    }

    public void update(String name, Kind kind, String location, Integer capacity, LocalTime opensAt, LocalTime closesAt,
            boolean needsApproval, boolean active) {
        if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt)) {
            throw new IllegalArgumentException("it must close after it opens");
        }
        if (capacity != null && capacity < 1) {
            throw new IllegalArgumentException("the capacity must be at least 1");
        }
        this.name = Require.text(name, "name", 150);
        this.kind = Require.notNull(kind, "kind");
        this.location = Require.optionalText(location, "location", 200);
        this.capacity = capacity;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.needsApproval = needsApproval;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Kind getKind() { return kind; }
    public String getLocation() { return location; }
    public Integer getCapacity() { return capacity; }
    public LocalTime getOpensAt() { return opensAt; }
    public LocalTime getClosesAt() { return closesAt; }
    public boolean isNeedsApproval() { return needsApproval; }
    public boolean isActive() { return active; }
}
