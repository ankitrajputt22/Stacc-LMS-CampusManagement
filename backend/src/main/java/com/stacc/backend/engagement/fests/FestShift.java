package com.stacc.backend.engagement.fests;

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

/** A volunteer shift of a fest: a role, a time, and how many volunteers it takes. */
@Entity
@Table(name = "campus_fest_shifts")
public class FestShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fest_id", nullable = false, updatable = false)
    private CampusFest fest;

    @Column(name = "role_title", nullable = false, length = 150)
    private String roleTitle;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int places;

    protected FestShift() {
        // Required by JPA.
    }

    public FestShift(CampusFest fest, String roleTitle, Instant startsAt, Instant endsAt, int places) {
        this.fest = Require.notNull(fest, "fest");
        this.roleTitle = Require.text(roleTitle, "Role", 150);
        Require.notNull(startsAt, "Start");
        Require.notNull(endsAt, "End");
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("A shift ends after it starts.");
        }
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.places = Require.range(places, "Places", 1, 500);
    }

    public Long getId() { return id; }
    public CampusFest getFest() { return fest; }
    public String getRoleTitle() { return roleTitle; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getPlaces() { return places; }
}
