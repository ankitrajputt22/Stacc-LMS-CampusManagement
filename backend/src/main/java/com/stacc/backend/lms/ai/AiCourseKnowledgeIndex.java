package com.stacc.backend.lms.ai;

import com.stacc.backend.lms.course.LmsCourse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * What Stacc knows about one LMS course's AI knowledge: the latest refresh's status and the fingerprint of the
 * published content indexed last time it succeeded. The vectors and the extracted text live only in the AI service;
 * nothing here holds course text, questions, or answers. The row is kept and updated, never deleted.
 */
@Entity
@Table(name = "ai_course_knowledge_indexes")
public class AiCourseKnowledgeIndex {

    public static final int MAX_ERROR_LENGTH = 1000;

    // No cascade: the LMS course is never created or deleted through its AI knowledge.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, unique = true, updatable = false)
    private LmsCourse lmsCourse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiKnowledgeIndexStatus status;

    @Column(name = "content_fingerprint", length = 64)
    private String contentFingerprint;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiCourseKnowledgeIndex() {
        // Required by JPA.
    }

    /** The first refresh of a course: it starts building. */
    public AiCourseKnowledgeIndex(LmsCourse lmsCourse) {
        if (lmsCourse == null) {
            throw new IllegalArgumentException("lmsCourse is required");
        }
        this.lmsCourse = lmsCourse;
        this.status = AiKnowledgeIndexStatus.BUILDING;
    }

    public Long getId() {
        return id;
    }

    public LmsCourse getLmsCourse() {
        return lmsCourse;
    }

    public AiKnowledgeIndexStatus getStatus() {
        return status;
    }

    /** The fingerprint of the content the last successful refresh indexed, or null if none has succeeded. */
    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** A new refresh starts. The previous successful fingerprint and time are kept until it succeeds. */
    public void startBuilding() {
        this.status = AiKnowledgeIndexStatus.BUILDING;
        this.lastError = null;
    }

    /** The refresh succeeded: the AI service now answers from an index of exactly this content. */
    public void ready(String contentFingerprint, Instant indexedAt) {
        if (contentFingerprint == null || !contentFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentFingerprint must be 64 lower-case hex characters");
        }
        this.status = AiKnowledgeIndexStatus.READY;
        this.contentFingerprint = contentFingerprint;
        this.indexedAt = indexedAt;
        this.lastError = null;
    }

    /** The refresh failed. The message must be a short client-safe text; it is cut to the column's length. */
    public void failed(String message) {
        this.status = AiKnowledgeIndexStatus.FAILED;
        String text = message == null || message.isBlank() ? "The refresh failed." : message.strip();
        this.lastError = text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }

    /**
     * Whether students and the assessment generator may use the index for content with this fingerprint: a refresh
     * has succeeded for exactly this content, and the latest refresh has not failed. While a new refresh builds, the
     * previous index keeps answering.
     */
    public boolean answersFor(String currentFingerprint) {
        return indexedAt != null && status != AiKnowledgeIndexStatus.FAILED
                && contentFingerprint != null && contentFingerprint.equals(currentFingerprint);
    }

    /** Whether published content changed since the last successful refresh. */
    public boolean isStaleFor(String currentFingerprint) {
        return contentFingerprint != null && !contentFingerprint.equals(currentFingerprint);
    }
}
