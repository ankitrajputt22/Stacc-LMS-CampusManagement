package com.stacc.backend.engagement.tournaments;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A tournament run by the campus office: a draft, then published, then archived; never deleted. */
@Entity
@Table(name = "campus_tournaments")
public class CampusTournament {

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String sport;

    @Column(nullable = false, length = 30)
    private String season;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected CampusTournament() {
    }

    public CampusTournament(String name, String sport, String season, Long createdBy, Instant createdAt) {
        this.name = text(name, "Enter the tournament's name.", 150);
        this.sport = text(sport, "Enter the sport.", 100);
        this.season = text(season, "Enter the season, such as 2026-27.", 30);
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    static String text(String value, String message, int max) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty() || stripped.length() > max) {
            throw new IllegalArgumentException(message);
        }
        return stripped;
    }

    /** Publishes a draft once; publishing again changes nothing. */
    public void publish(Instant at) {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This tournament is archived.");
        }
        if (status == Status.DRAFT) {
            status = Status.PUBLISHED;
            publishedAt = at;
        }
    }

    public void archive() {
        status = Status.ARCHIVED;
    }

    void requireOpen() {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This tournament is archived and can no longer be changed.");
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSport() { return sport; }
    public String getSeason() { return season; }
    public Status getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
}
