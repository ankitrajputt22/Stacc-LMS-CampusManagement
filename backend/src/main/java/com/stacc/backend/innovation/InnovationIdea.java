package com.stacc.backend.innovation;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
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

/** An idea: submitted, maybe shortlisted, then accepted or not selected once; or withdrawn by its submitter. Never deleted. */
@Entity
@Table(name = "innovation_ideas")
public class InnovationIdea {

    public enum Status { SUBMITTED, SHORTLISTED, ACCEPTED, NOT_SELECTED, WITHDRAWN }

    public static final int MAX_TEXT = 5_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitter_account_id", nullable = false, updatable = false)
    private UserAccount submitter;

    @Column(nullable = false, updatable = false, length = 200)
    private String title;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String problem;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String solution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUBMITTED;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    protected InnovationIdea() {
    }

    public InnovationIdea(UserAccount submitter, String title, String problem, String solution, Instant submittedAt) {
        this.submitter = submitter;
        this.title = text(title, "Give the idea a title.", 200);
        this.problem = text(problem, "Describe the problem it solves.", MAX_TEXT);
        this.solution = text(solution, "Describe the idea.", MAX_TEXT);
        this.submittedAt = submittedAt;
    }

    static String text(String value, String message, int max) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty() || stripped.length() > max) {
            throw new IllegalArgumentException(message);
        }
        return stripped;
    }

    /** Whether reviewers may still score it. */
    public boolean open() {
        return status == Status.SUBMITTED || status == Status.SHORTLISTED;
    }

    /** The office moves an open idea forward: submitted to shortlisted, or either to accepted or not selected. */
    public void decide(Status next, String note, Instant at) {
        if (!open() || next == Status.SUBMITTED || next == Status.WITHDRAWN || next == null || next == status) {
            throw new IllegalStateException("This idea cannot move to that decision.");
        }
        String text = note == null || note.isBlank() ? null : note.strip();
        if (text != null && text.length() > 1000) {
            throw new IllegalArgumentException("The note is at most 1,000 characters.");
        }
        status = next;
        decisionNote = text;
        decidedAt = at;
    }

    public void withdraw() {
        if (!open()) {
            throw new IllegalStateException("Only an idea still under review can be withdrawn.");
        }
        status = Status.WITHDRAWN;
    }

    public boolean decided() {
        return status == Status.ACCEPTED || status == Status.NOT_SELECTED;
    }

    public Long getId() { return id; }
    public UserAccount getSubmitter() { return submitter; }
    public String getTitle() { return title; }
    public String getProblem() { return problem; }
    public String getSolution() { return solution; }
    public Status getStatus() { return status; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
}
