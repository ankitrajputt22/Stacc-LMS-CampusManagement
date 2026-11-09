package com.stacc.backend.lms.assignment.submission;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One time a student handed in work for an assignment: optional plain text, and files kept as
 * attachments. An attempt never changes once it is made. Handing in again makes a new attempt with the
 * next number, and earlier attempts stay as they were.
 *
 * <p>The assignment's deadline at the moment of the attempt is kept with it, so whether the attempt was
 * late never changes, even if the deadline is moved afterwards.
 */
@Entity
@Table(name = "lms_submission_attempts")
public class LmsSubmissionAttempt {

    public static final int MAX_TEXT_LENGTH = 20_000;

    // Every column is fixed when the row is written: there are no setters and nothing is updatable.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, updatable = false)
    private LmsAssignmentSubmission submission;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    // Plain text exactly as typed, line breaks kept. It is never treated as HTML. Null when only files were sent.
    @Column(name = "text_content", updatable = false, columnDefinition = "MEDIUMTEXT")
    private String textContent;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "due_at_snapshot", nullable = false, updatable = false)
    private Instant dueAtSnapshot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LmsSubmissionAttempt() {
        // Required by JPA.
    }

    /**
     * @param textContent   plain text as typed; blank text counts as none
     * @param submittedAt   when the server accepted the attempt, never a time from the client
     * @param dueAtSnapshot the assignment's deadline at that moment
     */
    public LmsSubmissionAttempt(
            LmsAssignmentSubmission submission, int attemptNumber, String textContent, Instant submittedAt, Instant dueAtSnapshot) {
        this.submission = Require.notNull(submission, "submission");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be at least 1");
        }
        this.attemptNumber = attemptNumber;
        this.textContent = textOrNull(textContent);
        this.submittedAt = Require.notNull(submittedAt, "submittedAt");
        this.dueAtSnapshot = Require.notNull(dueAtSnapshot, "dueAtSnapshot");
    }

    /** The text as it will be stored: unchanged, or null when it is missing or blank. Too long is refused. */
    public static String textOrNull(String textContent) {
        if (textContent == null || textContent.isBlank()) {
            return null;
        }
        if (textContent.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("textContent must be at most " + MAX_TEXT_LENGTH + " characters");
        }
        return textContent;
    }

    public Long getId() {
        return id;
    }

    public LmsAssignmentSubmission getSubmission() {
        return submission;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getTextContent() {
        return textContent;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDueAtSnapshot() {
        return dueAtSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Late when it was accepted after the deadline that applied at the time. Exactly on the deadline is on time. */
    public boolean isLate() {
        return submittedAt.isAfter(dueAtSnapshot);
    }
}
