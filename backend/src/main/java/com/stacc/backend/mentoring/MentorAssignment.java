package com.stacc.backend.mentoring;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A faculty mentor paired with a student. Ended, never deleted; its meetings stay readable by the pair. */
@Entity
@Table(name = "mentor_assignments")
public class MentorAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_account_id", nullable = false, updatable = false)
    private UserAccount mentor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentee_account_id", nullable = false, updatable = false)
    private UserAccount mentee;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected MentorAssignment() {
    }

    public MentorAssignment(UserAccount mentor, UserAccount mentee, Instant startedAt) {
        if (mentor.getId().equals(mentee.getId())) {
            throw new IllegalArgumentException("A mentor cannot mentor themselves.");
        }
        this.mentor = mentor;
        this.mentee = mentee;
        this.startedAt = startedAt;
    }

    public void end(Instant at) {
        if (endedAt == null) {
            endedAt = at;
        }
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public Long getId() { return id; }
    public UserAccount getMentor() { return mentor; }
    public UserAccount getMentee() { return mentee; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
}
