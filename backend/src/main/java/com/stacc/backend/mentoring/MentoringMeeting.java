package com.stacc.backend.mentoring;

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

/** A meeting of a mentoring pair. Shared notes reach both; private notes reach the mentor only. */
@Entity
@Table(name = "mentoring_meetings")
public class MentoringMeeting {

    public static final int MAX_NOTES = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    private MentorAssignment assignment;

    @Column(name = "held_at", nullable = false)
    private Instant heldAt;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "shared_notes", columnDefinition = "TEXT")
    private String sharedNotes;

    @Column(name = "private_notes", columnDefinition = "TEXT")
    private String privateNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MentoringMeeting() {
    }

    public MentoringMeeting(MentorAssignment assignment, Instant heldAt, String title, String sharedNotes, String privateNotes, Instant createdAt) {
        if (!assignment.isActive()) {
            throw new IllegalStateException("This mentoring pair has ended.");
        }
        this.assignment = assignment;
        this.createdAt = createdAt;
        edit(heldAt, title, sharedNotes, privateNotes);
    }

    /** The mentor corrects the meeting's details and notes. */
    public void edit(Instant newHeldAt, String newTitle, String shared, String privateText) {
        String stripped = newTitle == null ? "" : newTitle.strip();
        if (stripped.isEmpty() || stripped.length() > 200) {
            throw new IllegalArgumentException("Enter a title for the meeting.");
        }
        if (newHeldAt == null) {
            throw new IllegalArgumentException("Enter when the meeting was held.");
        }
        this.heldAt = newHeldAt;
        this.title = stripped;
        this.sharedNotes = optional(shared);
        this.privateNotes = optional(privateText);
    }

    private static String optional(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.strip().length() > MAX_NOTES) {
            throw new IllegalArgumentException("Notes are at most 10,000 characters.");
        }
        return text.strip();
    }

    public Long getId() { return id; }
    public MentorAssignment getAssignment() { return assignment; }
    public Instant getHeldAt() { return heldAt; }
    public String getTitle() { return title; }
    public String getSharedNotes() { return sharedNotes; }
    public String getPrivateNotes() { return privateNotes; }
}
