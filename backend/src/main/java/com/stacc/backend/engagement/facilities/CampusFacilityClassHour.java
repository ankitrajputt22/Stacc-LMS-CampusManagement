package com.stacc.backend.engagement.facilities;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A weekly class slot of a facility, in the college's time zone: no bookings overlap it. Removed, never deleted. */
@Entity
@Table(name = "campus_facility_class_hours")
public class CampusFacilityClassHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false, updatable = false)
    private CampusFacility facility;

    @Column(name = "day_of_week", nullable = false, updatable = false)
    private int dayOfWeek;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private LocalTime startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private LocalTime endsAt;

    @Column(nullable = false, updatable = false, length = 100)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected CampusFacilityClassHour() {
    }

    public CampusFacilityClassHour(CampusFacility facility, DayOfWeek day, LocalTime startsAt, LocalTime endsAt, String label, Instant createdAt) {
        if (day == null || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("choose a day and a time that ends after it starts");
        }
        String stripped = label == null ? "" : label.strip();
        if (stripped.isEmpty() || stripped.length() > 100) {
            throw new IllegalArgumentException("name the class, such as CS101 lecture");
        }
        this.facility = facility;
        this.dayOfWeek = day.getValue();
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.label = stripped;
        this.createdAt = createdAt;
    }

    /** Whether this slot overlaps the given times on its day. */
    public boolean overlaps(LocalTime from, LocalTime to) {
        return from.isBefore(endsAt) && to.isAfter(startsAt);
    }

    public void remove(Instant at) {
        if (removedAt == null) {
            removedAt = at;
        }
    }

    public Long getId() { return id; }
    public CampusFacility getFacility() { return facility; }
    public DayOfWeek getDayOfWeek() { return DayOfWeek.of(dayOfWeek); }
    public LocalTime getStartsAt() { return startsAt; }
    public LocalTime getEndsAt() { return endsAt; }
    public String getLabel() { return label; }
    public Instant getRemovedAt() { return removedAt; }
}
