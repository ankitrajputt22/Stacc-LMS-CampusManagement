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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "ai_course_knowledge_indexes")
public class AiCourseKnowledgeIndex {

    public static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_kind", nullable = false, length = 30, updatable = false)
    private AiIndexKind kind;

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
    }

    public AiCourseKnowledgeIndex(LmsCourse lmsCourse) {
        this(lmsCourse, AiIndexKind.COURSE_CONTENT);
    }

    public AiCourseKnowledgeIndex(LmsCourse lmsCourse, AiIndexKind kind) {
        if (lmsCourse == null || kind == null) {
            throw new IllegalArgumentException("lmsCourse and kind are required");
        }
        this.lmsCourse = lmsCourse;
        this.kind = kind;
        this.status = AiKnowledgeIndexStatus.BUILDING;
    }

    public AiIndexKind getKind() {
        return kind;
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

    public void startBuilding() {
        this.status = AiKnowledgeIndexStatus.BUILDING;
        this.lastError = null;
    }

    public void ready(String contentFingerprint, Instant indexedAt) {
        if (contentFingerprint == null || !contentFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentFingerprint must be 64 lower-case hex characters");
        }
        this.status = AiKnowledgeIndexStatus.READY;
        this.contentFingerprint = contentFingerprint;
        this.indexedAt = indexedAt;
        this.lastError = null;
    }

    public void failed(String message) {
        this.status = AiKnowledgeIndexStatus.FAILED;
        String text = message == null || message.isBlank() ? "The refresh failed." : message.strip();
        this.lastError = text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }

    public boolean answersFor(String currentFingerprint) {
        return indexedAt != null && status != AiKnowledgeIndexStatus.FAILED
                && contentFingerprint != null && contentFingerprint.equals(currentFingerprint);
    }

    public boolean isStaleFor(String currentFingerprint) {
        return contentFingerprint != null && !contentFingerprint.equals(currentFingerprint);
    }
}
