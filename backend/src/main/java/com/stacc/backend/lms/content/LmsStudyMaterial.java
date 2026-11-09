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

/**
 * One study material of a topic: a titled file, such as lecture notes or a lab sheet. This row holds
 * the file's details only. The file itself is in private object storage under {@code objectKey}, a
 * key the application generated; the key is never sent to a browser. A new material is a draft. A
 * material that is no longer wanted is archived, and both the row and the stored file are kept.
 */
@Entity
@Table(name = "lms_study_materials")
public class LmsStudyMaterial {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_FILE_NAME_LENGTH = 255;
    public static final int MAX_OBJECT_KEY_LENGTH = 500;

    // No cascade: the topic is never created or deleted through one of its materials.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_topic_id", nullable = false, updatable = false)
    private LmsTopic lmsTopic;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    // Plain text. It is never treated as HTML.
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsStudyMaterial() {
        // Required by JPA.
    }

    /**
     * The stored file's details are fixed once the material exists. A wrong file is archived and a new
     * material is uploaded; there is no replacing a file in place.
     */
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

    /** Where the file is kept in object storage. Internal only: never send it to a client. */
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

    public boolean isArchived() {
        return status == LmsContentStatus.ARCHIVED;
    }

    /** Changes the title and the optional description. An archived material can no longer be changed. */
    public void edit(String title, String description) {
        requireNotArchived();
        this.title = Require.text(title, "title", MAX_TITLE_LENGTH);
        this.description = Require.optionalText(description, "description", MAX_DESCRIPTION_LENGTH);
    }

    /** Places the material in its topic's order. Positions start at 1. */
    public void moveTo(int position) {
        this.position = Require.range(position, "position", 1, Integer.MAX_VALUE);
    }

    /**
     * Publishing an already published material changes nothing. Students see it only once its topic and
     * module are published too: publishing a material never publishes them.
     */
    public void publish() {
        requireNotArchived();
        this.status = LmsContentStatus.PUBLISHED;
    }

    /** Archiving keeps the row and the stored file. Archiving it again changes nothing. */
    public void archive() {
        this.status = LmsContentStatus.ARCHIVED;
    }

    private void requireNotArchived() {
        if (isArchived()) {
            throw new IllegalStateException("An archived study material cannot be changed");
        }
    }
}
