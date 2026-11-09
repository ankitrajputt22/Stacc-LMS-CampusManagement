package com.stacc.backend.lms.message;

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

/** A file sent with a message. Its bytes are in private storage; it is downloaded only through its conversation. */
@Entity
@Table(name = "lms_conversation_message_attachments")
public class LmsMessageAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private LmsConversationMessage message;

    @Column(name = "object_key", nullable = false, updatable = false, length = 300)
    private String objectKey;

    @Column(name = "original_file_name", nullable = false, updatable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    protected LmsMessageAttachment() {
        // Required by JPA.
    }

    public LmsMessageAttachment(LmsConversationMessage message, StoredFile file) {
        this.message = Require.notNull(message, "message");
        this.objectKey = file.objectKey();
        this.fileName = file.fileName();
        this.contentType = file.contentType();
        this.sizeBytes = file.sizeBytes();
    }

    public Long getId() { return id; }
    public LmsConversationMessage getMessage() { return message; }
    public String getObjectKey() { return objectKey; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
}
