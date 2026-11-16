package com.stacc.backend.lms.announcement;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.attachment.LmsAttachmentStore.StoredFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A file on a course announcement. Its bytes are in private storage; it is downloaded only through its announcement's
 * own check. A removed file is kept, with its bytes, but no longer listed or downloaded.
 */
@Entity
@Table(name = "lms_announcement_attachments")
public class LmsAnnouncementAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "announcement_id", nullable = false, updatable = false)
    private LmsAnnouncement announcement;

    @Column(name = "object_key", nullable = false, updatable = false, length = 300)
    private String objectKey;

    @Column(name = "original_file_name", nullable = false, updatable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected LmsAnnouncementAttachment() {
        // Required by JPA.
    }

    public LmsAnnouncementAttachment(LmsAnnouncement announcement, StoredFile file, Instant now) {
        this.announcement = Require.notNull(announcement, "announcement");
        this.objectKey = file.objectKey();
        this.fileName = file.fileName();
        this.contentType = file.contentType();
        this.sizeBytes = file.sizeBytes();
        this.createdAt = Require.notNull(now, "now");
    }

    /** Stops listing the file; removing again keeps the first time. The stored bytes stay. */
    public void remove(Instant at) {
        if (removedAt == null) {
            this.removedAt = Require.notNull(at, "at");
        }
    }

    public boolean isRemoved() {
        return removedAt != null;
    }

    public Long getId() { return id; }
    public LmsAnnouncement getAnnouncement() { return announcement; }
    public String getObjectKey() { return objectKey; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
