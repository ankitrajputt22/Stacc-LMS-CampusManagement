package com.stacc.backend.hackathon;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A hackathon: a draft, published, results released once after it ends, then archived. Never deleted. */
@Entity
@Table(name = "hackathons")
public class Hackathon {

    public enum Status { DRAFT, PUBLISHED, RESULTS_RELEASED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "max_team_size", nullable = false)
    private int maxTeamSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "results_released_at")
    private Instant resultsReleasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Hackathon() {
    }

    public Hackathon(String title, String description, Instant startsAt, Instant endsAt, int maxTeamSize, Instant createdAt) {
        String stripped = title == null ? "" : title.strip();
        if (stripped.isEmpty() || stripped.length() > 200) {
            throw new IllegalArgumentException("Give the hackathon a title.");
        }
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("The hackathon must end after it starts.");
        }
        if (maxTeamSize < 1 || maxTeamSize > 10) {
            throw new IllegalArgumentException("Teams have 1 to 10 members.");
        }
        String text = description == null || description.isBlank() ? null : description.strip();
        if (text != null && text.length() > 5000) {
            throw new IllegalArgumentException("The description is at most 5,000 characters.");
        }
        this.title = stripped;
        this.description = text;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.maxTeamSize = maxTeamSize;
        this.createdAt = createdAt;
    }

    public void publish() {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Only a draft can be published.");
        }
        status = Status.PUBLISHED;
    }

    /** Releases the results once, after the hackathon has ended. */
    public void releaseResults(Instant now) {
        if (status != Status.PUBLISHED || now.isBefore(endsAt)) {
            throw new IllegalStateException("Results are released once, after the hackathon has ended.");
        }
        status = Status.RESULTS_RELEASED;
        resultsReleasedAt = now;
    }

    public void archive() {
        status = Status.ARCHIVED;
    }

    /** Teams form and hand in work while the hackathon is published and has not ended. */
    public boolean takingWork(Instant now) {
        return status == Status.PUBLISHED && now.isBefore(endsAt);
    }

    /** Judges score once it has ended and until results are released. */
    public boolean judging(Instant now) {
        return status == Status.PUBLISHED && !now.isBefore(endsAt);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getMaxTeamSize() { return maxTeamSize; }
    public Status getStatus() { return status; }
    public Instant getResultsReleasedAt() { return resultsReleasedAt; }
}
