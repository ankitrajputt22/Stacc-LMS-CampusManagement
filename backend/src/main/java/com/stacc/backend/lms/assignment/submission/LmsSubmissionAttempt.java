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

@Entity
@Table(name = "lms_submission_attempts")
public class LmsSubmissionAttempt {

    public static final int MAX_TEXT_LENGTH = 20_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, updatable = false)
    private LmsAssignmentSubmission submission;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

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
    }

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

    public boolean isLate() {
        return submittedAt.isAfter(dueAtSnapshot);
    }
}
