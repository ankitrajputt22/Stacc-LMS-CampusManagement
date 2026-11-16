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

/** A team in one hackathon, with a name unique within it. */
@Entity
@Table(name = "hackathon_teams")
public class HackathonTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hackathon_id", nullable = false, updatable = false)
    private Hackathon hackathon;

    @Column(nullable = false, updatable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HackathonTeam() {
    }

    public HackathonTeam(Hackathon hackathon, String name, Instant createdAt) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty() || stripped.length() > 100) {
            throw new IllegalArgumentException("Name the team.");
        }
        this.hackathon = hackathon;
        this.name = stripped;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Hackathon getHackathon() { return hackathon; }
    public String getName() { return name; }
}
