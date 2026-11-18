package com.stacc.backend.lms.content;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "lms_study_materials")
public class LmsStudyMaterial {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_FILE_NAME_LENGTH = 255;
    public static final int MAX_OBJECT_KEY_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_topic_id", nullable = false, updatable = false)
    private LmsTopic lmsTopic;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(name = "object_key", nullable = false, unique = true, updatable = false, length = MAX_OBJECT_KEY_LENGTH)
    private String objectKey;

    @Column(name = "original_file_name", nullable = false, updatable = false, length = MAX_FILE_NAME_LENGTH)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmsContentStatus status = LmsContentStatus.DRAFT;

    @Column(name = "excluded_from_ai", nullable = false)
    private boolean excludedFromAi;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsStudyMaterial() {
    }

    public LmsStudyMaterial(
            LmsTopic lmsTopic,
            String title,
            String description,
            String objectKey,
            String originalFileName,
            String contentType,
            long sizeBytes,
            int position) {
        this.lmsTopic = Require.notNull(lmsTopic, "lmsTopic");
        edit(title, description);
        this.objectKey = Require.text(objectKey, "objectKey", MAX_OBJECT_KEY_LENGTH);
        this.originalFileName = Require.text(originalFileName, "originalFileName", MAX_FILE_NAME_LENGTH);
        this.contentType = Require.text(contentType, "contentType", 100);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes cannot be negative");
        }
        this.sizeBytes = sizeBytes;
        moveTo(position);
    }

    public Long getId() {
        return id;
    }

    public LmsTopic getLmsTopic() {
        return lmsTopic;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

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

    public int getPosition() {
        return position;
    }

    public LmsContentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isExcludedFromAi() {
        return excludedFromAi;
    }

    public void changeAiExclusion(boolean excluded) {
        requireNotArchived();
        this.excludedFromAi = excluded;
    }

    public boolean isArchived() {
        return status == LmsContentStatus.ARCHIVED;
    }

    public void edit(String title, String description) {
        requireNotArchived();
        this.title = Require.text(title, "title", MAX_TITLE_LENGTH);
        this.description = Require.optionalText(description, "description", MAX_DESCRIPTION_LENGTH);
    }

    public void moveTo(int position) {
        this.position = Require.range(position, "position", 1, Integer.MAX_VALUE);
    }

    public void publish() {
        requireNotArchived();
        this.status = LmsContentStatus.PUBLISHED;
    }

    public void archive() {
        this.status = LmsContentStatus.ARCHIVED;
    }

    private void requireNotArchived() {
        if (isArchived()) {
            throw new IllegalStateException("An archived study material cannot be changed");
        }
    }
}
