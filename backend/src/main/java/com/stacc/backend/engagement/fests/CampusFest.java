package com.stacc.backend.engagement.fests;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** A fest: a few days of the college calendar with a schedule of campus events. Draft, published, or archived. */
@Entity
@Table(name = "campus_fests")
public class CampusFest {

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.DRAFT;

    // The events on the fest's schedule, by ID; each event belongs to at most one fest.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campus_fest_events", joinColumns = @JoinColumn(name = "fest_id"))
    @Column(name = "event_id")
    private Set<Long> eventIds = new LinkedHashSet<>();

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CampusFest() {
        // Required by JPA.
    }

    public CampusFest(String name, String description, LocalDate startsOn, LocalDate endsOn, Long by, Instant now) {
        this.createdByAccountId = Require.notNull(by, "createdBy");
        this.createdAt = Require.notNull(now, "now");
        edit(name, description, startsOn, endsOn);
    }

    public void edit(String newName, String newDescription, LocalDate start, LocalDate end) {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This fest is archived and can no longer be changed.");
        }
        this.name = Require.text(newName, "Name", 150);
        this.description = Require.optionalText(newDescription, "Description", 10_000);
        Require.notNull(start, "First day");
        Require.notNull(end, "Last day");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("A fest ends on or after its first day.");
        }
        this.startsOn = start;
        this.endsOn = end;
    }

    public void publish() {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This fest is archived and can no longer be published.");
        }
        status = Status.PUBLISHED;
    }

    public void archive() {
        status = Status.ARCHIVED;
    }

    public void schedule(Long eventId) {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This fest is archived and can no longer be changed.");
        }
        eventIds.add(Require.notNull(eventId, "eventId"));
    }

    public void unschedule(Long eventId) {
        eventIds.remove(eventId);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public Status getStatus() { return status; }
    public Set<Long> getEventIds() { return Set.copyOf(eventIds); }
}
