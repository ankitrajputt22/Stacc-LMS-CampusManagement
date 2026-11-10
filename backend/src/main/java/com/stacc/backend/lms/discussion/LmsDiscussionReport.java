package com.stacc.backend.lms.discussion;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
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

/**
 * A student's report of a discussion post (a thread, or one of its replies) to the course's faculty, with a reason.
 * It is resolved once, by hiding the post or dismissing the report, and never deleted. A person has at most one open
 * report per post.
 */
@Entity
@Table(name = "lms_discussion_reports")
public class LmsDiscussionReport {

    public static final int MAX_REASON_LENGTH = 500;

    public enum Resolution {
        HIDDEN,
        DISMISSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false, updatable = false)
    private LmsDiscussionThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_id", updatable = false)
    private LmsDiscussionReply reply;

    @Column(name = "reporter_account_id", nullable = false, updatable = false)
    private Long reporterAccountId;

    @Column(nullable = false, updatable = false, length = MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_account_id")
    private Long resolvedByAccountId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Resolution resolution;

    protected LmsDiscussionReport() {
        // Required by JPA.
    }

    public LmsDiscussionReport(LmsDiscussionThread thread, LmsDiscussionReply reply, Long reporterAccountId, String reason, Instant now) {
        this.thread = Require.notNull(thread, "thread");
        this.reply = reply;
        this.reporterAccountId = Require.notNull(reporterAccountId, "reporterAccountId");
        this.reason = Require.text(reason, "Reason", MAX_REASON_LENGTH);
        this.createdAt = Require.notNull(now, "now");
    }

    /** Resolves an open report once; a resolved report never changes again. */
    public void resolve(Resolution resolution, Long byAccountId, Instant at) {
        if (resolvedAt != null) {
            return;
        }
        this.resolution = Require.notNull(resolution, "resolution");
        this.resolvedByAccountId = Require.notNull(byAccountId, "byAccountId");
        this.resolvedAt = Require.notNull(at, "at");
    }

    public boolean isOpen() {
        return resolvedAt == null;
    }

    public Long getId() { return id; }
    public LmsDiscussionThread getThread() { return thread; }
    public LmsDiscussionReply getReply() { return reply; }
    public Long getReporterAccountId() { return reporterAccountId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Resolution getResolution() { return resolution; }
}
