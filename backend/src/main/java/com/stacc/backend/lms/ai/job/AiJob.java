package com.stacc.backend.lms.ai.job;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_jobs")
public class AiJob {

    public enum Kind { REFRESH_INDEX }

    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private Kind kind;

    @Column(name = "lms_course_id", nullable = false, updatable = false)
    private Long lmsCourseId;

    @Column(name = "requested_by_account_id", nullable = false, updatable = false)
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.QUEUED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "result_summary", length = 200)
    private String resultSummary;

    @Column(name = "error_message", length = 300)
    private String errorMessage;

    protected AiJob() {
    }

    public AiJob(Kind kind, Long lmsCourseId, Long requestedBy, Instant createdAt) {
        this.kind = kind;
        this.lmsCourseId = lmsCourseId;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt;
    }

    void start(Instant at) {
        if (status != Status.QUEUED) {
            throw new IllegalStateException("Only a queued job starts.");
        }
        status = Status.RUNNING;
        startedAt = at;
    }

    void succeed(String summary, Instant at) {
        status = Status.SUCCEEDED;
        resultSummary = cut(summary, 200);
        finishedAt = at;
    }

    void fail(String message, Instant at) {
        status = Status.FAILED;
        errorMessage = cut(message, 300);
        finishedAt = at;
    }

    private static String cut(String text, int max) {
        return text == null ? null : text.length() > max ? text.substring(0, max - 1) + "…" : text;
    }

    public boolean live() {
        return status == Status.QUEUED || status == Status.RUNNING;
    }

    public Long getId() { return id; }
    public Kind getKind() { return kind; }
    public Long getLmsCourseId() { return lmsCourseId; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getResultSummary() { return resultSummary; }
    public String getErrorMessage() { return errorMessage; }
}
