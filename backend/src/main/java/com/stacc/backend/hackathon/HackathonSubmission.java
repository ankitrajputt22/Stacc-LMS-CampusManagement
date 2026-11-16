package com.stacc.backend.hackathon;

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

/** A team's one submission, replaced by its members until the hackathon ends. */
@Entity
@Table(name = "hackathon_submissions")
public class HackathonSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, updatable = false)
    private HackathonTeam team;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(length = 500)
    private String link;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected HackathonSubmission() {
    }

    public HackathonSubmission(HackathonTeam team) {
        this.team = team;
    }

    public void set(String newTitle, String newSummary, String newLink, Instant at) {
        String stripped = newTitle == null ? "" : newTitle.strip();
        if (stripped.isEmpty() || stripped.length() > 200) {
            throw new IllegalArgumentException("Give the submission a title.");
        }
        String text = newSummary == null ? "" : newSummary.strip();
        if (text.isEmpty() || text.length() > 5000) {
            throw new IllegalArgumentException("Describe what you built in up to 5,000 characters.");
        }
        String url = newLink == null || newLink.isBlank() ? null : newLink.strip();
        if (url != null && (!url.startsWith("https://") || url.length() > 500 || url.chars().anyMatch(Character::isWhitespace))) {
            throw new IllegalArgumentException("A link must be one https:// address.");
        }
        this.title = stripped;
        this.summary = text;
        this.link = url;
        this.submittedAt = at;
    }

    public Long getId() { return id; }
    public HackathonTeam getTeam() { return team; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getLink() { return link; }
    public Instant getSubmittedAt() { return submittedAt; }
}
