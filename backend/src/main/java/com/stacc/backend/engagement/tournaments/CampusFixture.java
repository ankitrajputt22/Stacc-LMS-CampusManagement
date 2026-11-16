package com.stacc.backend.engagement.tournaments;

import java.time.Instant;

import com.stacc.backend.engagement.teams.CampusTeam;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A match in a tournament between two sides, each one of our teams or an external opponent named in text. A played
 * fixture keeps text scores and a result, which may be corrected; a cancelled one stays listed. Never deleted.
 */
@Entity
@Table(name = "campus_fixtures")
public class CampusFixture {

    public enum Status { SCHEDULED, PLAYED, CANCELLED }

    public enum Result { HOME_WIN, AWAY_WIN, DRAW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false, updatable = false)
    private CampusTournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", updatable = false)
    private CampusTeam homeTeam;

    @Column(name = "home_name", updatable = false, length = 150)
    private String homeName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", updatable = false)
    private CampusTeam awayTeam;

    @Column(name = "away_name", updatable = false, length = 150)
    private String awayName;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(updatable = false, length = 200)
    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;

    @Column(name = "home_score", length = 50)
    private String homeScore;

    @Column(name = "away_score", length = 50)
    private String awayScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Result result;

    @Column(name = "recorded_at")
    private Instant recordedAt;

    @Column(name = "recorded_by_account_id")
    private Long recordedBy;

    protected CampusFixture() {
    }

    /** Each side is one of our teams or an external opponent's name, and the two sides differ. */
    public CampusFixture(CampusTournament tournament, CampusTeam homeTeam, String homeName, CampusTeam awayTeam, String awayName,
            Instant startsAt, String venue) {
        tournament.requireOpen();
        this.tournament = tournament;
        this.homeTeam = homeTeam;
        this.homeName = homeTeam == null ? CampusTournament.text(homeName, "Choose our team or name the home side.", 150) : null;
        this.awayTeam = awayTeam;
        this.awayName = awayTeam == null ? CampusTournament.text(awayName, "Choose our team or name the away side.", 150) : null;
        if (homeTeam != null && awayTeam != null && homeTeam.getId().equals(awayTeam.getId())
                || this.homeName != null && this.homeName.equalsIgnoreCase(this.awayName)) {
            throw new IllegalArgumentException("A team cannot play itself.");
        }
        if (startsAt == null) {
            throw new IllegalArgumentException("Enter when the match starts.");
        }
        this.startsAt = startsAt;
        this.venue = venue == null || venue.isBlank() ? null : venue.strip();
    }

    /** Records or corrects the scores (as text, such as "152/6") and the result. */
    public void record(String home, String away, Result outcome, Long by, Instant at) {
        tournament.requireOpen();
        if (status == Status.CANCELLED) {
            throw new IllegalStateException("This match was cancelled.");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("Choose the result.");
        }
        this.homeScore = CampusTournament.text(home, "Enter the home side's score.", 50);
        this.awayScore = CampusTournament.text(away, "Enter the away side's score.", 50);
        this.result = outcome;
        this.status = Status.PLAYED;
        this.recordedBy = by;
        this.recordedAt = at;
    }

    public void cancel() {
        tournament.requireOpen();
        if (status == Status.PLAYED) {
            throw new IllegalStateException("A played match cannot be cancelled; correct its result instead.");
        }
        status = Status.CANCELLED;
    }

    public Long getId() { return id; }
    public CampusTournament getTournament() { return tournament; }
    public CampusTeam getHomeTeam() { return homeTeam; }
    public String getHomeName() { return homeName; }
    public CampusTeam getAwayTeam() { return awayTeam; }
    public String getAwayName() { return awayName; }
    public Instant getStartsAt() { return startsAt; }
    public String getVenue() { return venue; }
    public Status getStatus() { return status; }
    public String getHomeScore() { return homeScore; }
    public String getAwayScore() { return awayScore; }
    public Result getResult() { return result; }
}
