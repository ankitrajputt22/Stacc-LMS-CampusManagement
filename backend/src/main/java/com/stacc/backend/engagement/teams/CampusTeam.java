package com.stacc.backend.engagement.teams;

import java.time.Instant;

import com.stacc.backend.engagement.groups.CampusGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A team of a sports group for one season. Made inactive, never deleted; its roster is kept. */
@Entity
@Table(name = "campus_teams")
public class CampusTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private CampusGroup group;

    @Column(nullable = false, updatable = false, length = 150)
    private String name;

    @Column(nullable = false, updatable = false, length = 30)
    private String season;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CampusTeam() {
    }

    public CampusTeam(CampusGroup group, String name, String season, Long createdBy, Instant createdAt) {
        if (group.getKind() != CampusGroup.Kind.SPORT) {
            throw new IllegalStateException("Teams belong to sports groups.");
        }
        if (!group.isActive()) {
            throw new IllegalStateException("This group is inactive.");
        }
        this.group = group;
        this.name = required(name, "Enter the team's name.", 150);
        this.season = required(season, "Enter the season, such as 2026-27.", 30);
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    private static String required(String value, String message, int max) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty() || stripped.length() > max) {
            throw new IllegalArgumentException(message);
        }
        return stripped;
    }

    public void deactivate() {
        active = false;
    }

    public Long getId() { return id; }
    public CampusGroup getGroup() { return group; }
    public String getName() { return name; }
    public String getSeason() { return season; }
    public boolean isActive() { return active; }
}
