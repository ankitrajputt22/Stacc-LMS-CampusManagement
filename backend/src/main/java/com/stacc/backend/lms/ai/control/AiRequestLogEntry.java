package com.stacc.backend.lms.ai.control;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One AI request without its content (v0.13.7): what kind, which course, how it ended, and how long it took. Never the
 * question, answer, sources, or who asked.
 */
@Entity
@Table(name = "ai_request_log")
public class AiRequestLogEntry {

    public enum Kind { ASK, SUMMARY, PRACTICE, ASSESSMENT_DRAFT, QUESTION_BANK, MODEL_ANSWER, RUBRIC, FLASHCARDS }

    public enum Outcome { SUCCEEDED, INSUFFICIENT_CONTEXT, REFUSED, BUSY, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Kind kind;

    @Column(name = "lms_course_id", nullable = false, updatable = false)
    private Long lmsCourseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Outcome outcome;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private int durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiRequestLogEntry() {
        // Required by JPA.
    }

    public AiRequestLogEntry(Kind kind, Long lmsCourseId, Outcome outcome, int durationMs, Instant createdAt) {
        this.kind = kind;
        this.lmsCourseId = lmsCourseId;
        this.outcome = outcome;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }

    public Kind getKind() {
        return kind;
    }

    public Outcome getOutcome() {
        return outcome;
    }
}
