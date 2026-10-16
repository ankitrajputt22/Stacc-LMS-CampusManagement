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
 * One file handed in with an attempt. Only its details are kept here; the bytes are in private object
 * storage under {@code objectKey}, a key the server generates. The key is internal: it is never sent to a
 * client. Like its attempt, an attachment never changes once it is made.
 */
@Entity
@Table(name = "lms_submission_attachments")
public class LmsSubmissionAttachment {

    public static final int MAX_FILE_NAME_LENGTH = 255;
    public static final int MAX_OBJECT_KEY_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, updatable = false)
    private LmsSubmissionAttempt attempt;

    @Column(name = "object_key", nullable = false, unique = true, updatable = false, length = MAX_OBJECT_KEY_LENGTH)
    private String objectKey;

    @Column(name = "original_file_name", nullable = false, updatable = false, length = MAX_FILE_NAME_LENGTH)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LmsSubmissionAttachment() {
        // Required by JPA.
    }

    public LmsSubmissionAttachment(
            LmsSubmissionAttempt attempt, String objectKey, String originalFileName, String contentType, long sizeBytes) {
        this.attempt = Require.notNull(attempt, "attempt");
        this.objectKey = Require.text(objectKey, "objectKey", MAX_OBJECT_KEY_LENGTH);
        this.originalFileName = Require.text(originalFileName, "originalFileName", MAX_FILE_NAME_LENGTH);
        this.contentType = Require.text(contentType, "contentType", 100);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        this.sizeBytes = sizeBytes;
    }

    public Long getId() {
        return id;
    }

    public LmsSubmissionAttempt getAttempt() {
        return attempt;
    }

    /** Where the bytes are stored. For the server only; never part of an answer to a client. */
    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
