package com.stacc.backend.documents;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.attachment.LmsAttachmentStore.StoredFile;
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

/** One document in a person's vault: issued by the college, or uploaded by them. The file is in private storage. */
@Entity
@Table(name = "vault_documents")
public class VaultDocument {

    public enum Origin { ISSUED, UPLOADED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_account_id", nullable = false, updatable = false)
    private UserAccount owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Origin origin;

    @Column(nullable = false, updatable = false, length = 200)
    private String title;

    @Column(name = "object_key", nullable = false, updatable = false, length = 300)
    private String objectKey;

    @Column(name = "file_name", nullable = false, updatable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_user_account_id", updatable = false)
    private UserAccount issuedBy;

    @Column(name = "verification_fingerprint", length = 64)
    private String verificationFingerprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_request_id", updatable = false)
    private CertificateRequest certificateRequest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected VaultDocument() {
        // Required by JPA.
    }

    public VaultDocument(UserAccount owner, Origin origin, String title, StoredFile file, UserAccount issuedBy,
            CertificateRequest request, Instant now) {
        this.owner = owner;
        this.origin = origin;
        this.title = Require.text(title, "title", 200);
        this.objectKey = file.objectKey();
        this.fileName = file.fileName();
        this.contentType = file.contentType();
        this.sizeBytes = file.sizeBytes();
        this.issuedBy = issuedBy;
        this.certificateRequest = request;
        this.createdAt = now;
    }

    public void verifiableBy(String fingerprint) {
        this.verificationFingerprint = fingerprint;
    }

    public void withdraw(Instant at) {
        if (withdrawnAt == null) {
            withdrawnAt = at;
        }
    }

    public Long getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public Origin getOrigin() { return origin; }
    public String getTitle() { return title; }
    public String getObjectKey() { return objectKey; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public UserAccount getIssuedBy() { return issuedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
}
