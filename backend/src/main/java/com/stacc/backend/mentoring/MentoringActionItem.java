package com.stacc.backend.mentoring;

import java.time.Instant;

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

/** Something to do after a meeting, owned by the mentor or the student; completed once, never deleted. */
@Entity
@Table(name = "mentoring_action_items")
public class MentoringActionItem {

    public enum Owner { MENTOR, MENTEE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false, updatable = false)
    private MentoringMeeting meeting;

    @Column(nullable = false, updatable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Owner owner;

    @Column(name = "due_at", updatable = false)
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MentoringActionItem() {
    }

    public MentoringActionItem(MentoringMeeting meeting, String description, Owner owner, Instant dueAt, Instant createdAt) {
        String stripped = description == null ? "" : description.strip();
        if (stripped.isEmpty() || stripped.length() > 500) {
            throw new IllegalArgumentException("Describe the action item.");
        }
        if (owner == null) {
            throw new IllegalArgumentException("Choose who does it.");
        }
        this.meeting = meeting;
        this.description = stripped;
        this.owner = owner;
        this.dueAt = dueAt;
        this.createdAt = createdAt;
    }

    /** Completes the item; completing again changes nothing. */
    public void complete(Instant at) {
        if (completedAt == null) {
            completedAt = at;
        }
    }

    public Long getId() { return id; }
    public MentoringMeeting getMeeting() { return meeting; }
    public String getDescription() { return description; }
    public Owner getOwner() { return owner; }
    public Instant getDueAt() { return dueAt; }
    public Instant getCompletedAt() { return completedAt; }
}
