package com.stacc.backend.engagement.groups;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
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

/** A meeting or practice of a club or sports group, shown on its members' calendars. Cancelled, never deleted. */
@Entity
@Table(name = "engagement_group_sessions")
public class GroupSession {

    public enum Kind {
        MEETING,
        PRACTICE
    }

    public enum Status {
        SCHEDULED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private CampusGroup group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Kind kind;

    @Column(nullable = false, updatable = false, length = 200)
    private String title;

    @Column(updatable = false, length = 200)
    private String location;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.SCHEDULED;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GroupSession() {
        // Required by JPA.
    }

    public GroupSession(CampusGroup group, Kind kind, String title, String location, Instant startsAt, Instant endsAt, Long by, Instant now) {
        this.group = Require.notNull(group, "group");
        this.kind = Require.notNull(kind, "Kind");
        this.title = Require.text(title, "Title", 200);
        this.location = Require.optionalText(location, "Location", 200);
        this.startsAt = Require.notNull(startsAt, "Start");
        this.endsAt = Require.notNull(endsAt, "End");
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("A session must end after it starts.");
        }
        this.createdByAccountId = Require.notNull(by, "createdBy");
        this.createdAt = Require.notNull(now, "now");
    }

    public void cancel() {
        status = Status.CANCELLED;
    }

    public Long getId() { return id; }
    public CampusGroup getGroup() { return group; }
    public Kind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Status getStatus() { return status; }
}
