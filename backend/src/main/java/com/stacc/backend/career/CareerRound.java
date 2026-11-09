package com.stacc.backend.career;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** When and where one stage of an opportunity takes place. */
@Entity
@Table(name = "career_rounds")
public class CareerRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false, updatable = false)
    private CareerOpportunity opportunity;

    @Column(name = "stage_index", nullable = false, updatable = false)
    private int stageIndex;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(length = 200)
    private String venue;

    protected CareerRound() {
        // Required by JPA.
    }

    public CareerRound(CareerOpportunity opportunity, int stageIndex, Instant startsAt, String venue) {
        this.opportunity = opportunity;
        this.stageIndex = stageIndex;
        schedule(startsAt, venue);
    }

    public void schedule(Instant startsAt, String venue) {
        this.startsAt = startsAt;
        this.venue = venue == null || venue.isBlank() ? null : venue.strip();
    }

    public CareerOpportunity getOpportunity() { return opportunity; }
    public int getStageIndex() { return stageIndex; }
    public Instant getStartsAt() { return startsAt; }
    public String getVenue() { return venue; }
}
