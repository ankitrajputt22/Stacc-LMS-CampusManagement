package com.stacc.backend.career;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.attachment.LmsAttachmentStore.StoredFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One resume version. Never changed or deleted, so an application keeps the exact resume it was sent with. */
@Entity
@Table(name = "career_resumes")
public class CareerResume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(nullable = false, updatable = false, length = 100)
    private String label;

    @Column(name = "object_key", nullable = false, updatable = false, length = 300)
    private String objectKey;

    @Column(name = "file_name", nullable = false, updatable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected CareerResume() {
        // Required by JPA.
    }

    public CareerResume(Long accountId, String label, StoredFile file, Instant now) {
        this.accountId = accountId;
        this.label = Require.text(label, "label", 100);
        this.objectKey = file.objectKey();
        this.fileName = file.fileName();
        this.contentType = file.contentType();
        this.sizeBytes = file.sizeBytes();
        this.createdAt = now;
    }

    public void archive(Instant at) {
        if (archivedAt == null) {
            archivedAt = at;
        }
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public String getLabel() { return label; }
    public String getObjectKey() { return objectKey; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getArchivedAt() { return archivedAt; }
}
