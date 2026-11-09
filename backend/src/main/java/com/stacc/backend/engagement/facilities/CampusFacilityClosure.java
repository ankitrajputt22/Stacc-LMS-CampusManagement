package com.stacc.backend.engagement.facilities;

import java.time.Instant;
import java.time.LocalDate;

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

/** Days a facility is closed (end included), with the reason. New bookings on those days are refused. */
@Entity
@Table(name = "campus_facility_closures")
public class CampusFacilityClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false, updatable = false)
    private CampusFacility facility;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CampusFacilityClosure() {
        // Required by JPA.
    }

    public CampusFacilityClosure(CampusFacility facility, LocalDate startDate, LocalDate endDate, String reason, Instant now) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("the closure must end on or after it starts");
        }
        this.facility = facility;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = Require.text(reason, "reason", 500);
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getReason() { return reason; }
}
