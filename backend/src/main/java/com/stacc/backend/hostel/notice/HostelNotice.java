package com.stacc.backend.hostel.notice;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.hostel.Hostel;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A plain-text notice for one hostel, or one block of it. Drafts change freely; a published notice can still be
 * corrected and keeps its first publication time; an archived one is final. Its hostel and block never change.
 */
@Entity
@Table(name = "hostel_notices")
public class HostelNotice {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_MESSAGE_LENGTH = 5_000;

    public enum Status {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hostel_id", nullable = false, updatable = false)
    private Hostel hostel;

    @Column(updatable = false, length = 50)
    private String block;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private boolean emergency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdByAccountId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HostelNotice() {
        // Required by JPA.
    }

    public HostelNotice(Hostel hostel, String block, String title, String message, boolean emergency, Long createdByAccountId) {
        this.hostel = Require.notNull(hostel, "hostel");
        this.block = Require.optionalText(block, "Block", 50);
        this.createdByAccountId = Require.notNull(createdByAccountId, "createdByAccountId");
        edit(title, message, emergency);
    }

    /** Changes the text and the emergency flag of a draft, or corrects a published notice. */
    public void edit(String title, String message, boolean emergency) {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This notice is archived and can no longer be changed.");
        }
        if (status == Status.PUBLISHED && emergency != this.emergency) {
            throw new IllegalStateException("Whether a published notice is an emergency no longer changes.");
        }
        this.title = Require.text(title, "Title", MAX_TITLE_LENGTH);
        this.message = Require.text(message, "Message", MAX_MESSAGE_LENGTH);
        this.emergency = emergency;
    }

    /** Publishes a draft; returns whether this was its first publication. Publishing again changes nothing. */
    public boolean publish(Instant at) {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This notice is archived and can no longer be published.");
        }
        if (status == Status.PUBLISHED) {
            return false;
        }
        status = Status.PUBLISHED;
        boolean first = publishedAt == null;
        if (first) {
            publishedAt = Require.notNull(at, "at");
        }
        return first;
    }

    public void archive() {
        status = Status.ARCHIVED;
    }

    /** Whether the notice reaches a resident of this block of its hostel: a hostel-wide notice reaches everyone. */
    public boolean reaches(String residentBlock) {
        return block == null || block.equalsIgnoreCase(residentBlock == null ? "" : residentBlock.strip());
    }

    public Long getId() { return id; }
    public Hostel getHostel() { return hostel; }
    public String getBlock() { return block; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public boolean isEmergency() { return emergency; }
    public Status getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
