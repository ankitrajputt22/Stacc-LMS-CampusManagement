package com.stacc.backend.report.export;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One background export: who asked, what, its state, and its private file until it expires. */
@Entity
@Table(name = "report_exports")
public class ReportExport {

    public enum Kind { ADMIN_ENROLLMENTS, COURSE_ANALYTICS }

    public enum Status { QUEUED, RUNNING, READY, FAILED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private Kind kind;

    @Column(name = "parameter_value", updatable = false)
    private Long parameter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.QUEUED;

    @Column(name = "object_key", length = 300)
    private String objectKey;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(length = 300)
    private String failure;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected ReportExport() {
        // Required by JPA.
    }

    public ReportExport(Long accountId, Kind kind, Long parameter, Instant now, Instant expiresAt) {
        this.accountId = accountId;
        this.kind = kind;
        this.parameter = parameter;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    void running() {
        status = Status.RUNNING;
    }

    void ready(String objectKey, String fileName, long sizeBytes, Instant at) {
        this.status = Status.READY;
        this.objectKey = objectKey;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.finishedAt = at;
    }

    void failed(String reason, Instant at) {
        this.status = Status.FAILED;
        this.failure = reason;
        this.finishedAt = at;
    }

    void expired() {
        this.status = Status.EXPIRED;
        this.objectKey = null;
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public Kind getKind() { return kind; }
    public Long getParameter() { return parameter; }
    public Status getStatus() { return status; }
    public String getObjectKey() { return objectKey; }
    public String getFileName() { return fileName; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getFailure() { return failure; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
