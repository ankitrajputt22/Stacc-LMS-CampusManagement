package com.stacc.backend.campus.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.stacc.backend.approval.ApprovalRequest;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.documents.VaultDocument;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "campus_service_requests")
public class CampusServiceRequest {

    public static final int MAX_SUBJECT_LENGTH = 200;
    public static final int MAX_DETAILS_LENGTH = 10_000;
    public static final String NUMBER_FORMAT = "CSR-%06d";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", unique = true, length = 20)
    private String requestNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_definition_id", nullable = false, updatable = false)
    private CampusServiceDefinition serviceDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_account_id", nullable = false, updatable = false)
    private UserAccount requester;

    @Column(nullable = false, updatable = false, length = MAX_SUBJECT_LENGTH)
    private String subject;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampusServiceRequestStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id", unique = true)
    private ApprovalRequest approvalRequest;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_document_id", unique = true)
    private VaultDocument issuedDocument;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CampusServiceRequest() {
    }

    public CampusServiceRequest(CampusServiceDefinition serviceDefinition, UserAccount requester, String subject,
            String details, Instant submittedAt) {
        this.serviceDefinition = Require.notNull(serviceDefinition, "serviceDefinition");
        this.requester = Require.notNull(requester, "requester");
        this.subject = Require.text(subject, "subject", MAX_SUBJECT_LENGTH);
        this.details = Require.text(details, "details", MAX_DETAILS_LENGTH);
        this.submittedAt = Require.notNull(submittedAt, "submittedAt").truncatedTo(ChronoUnit.MICROS);
        this.status = serviceDefinition.isApprovalRequired() ? CampusServiceRequestStatus.SUBMITTED
                : CampusServiceRequestStatus.APPROVED;
    }

    public static String numberFor(long id) {
        return NUMBER_FORMAT.formatted(id);
    }

    public void assignNumber() {
        if (requestNumber == null) {
            requestNumber = numberFor(Require.notNull(id, "id"));
        }
    }

    public void linkApproval(ApprovalRequest request) {
        if (approvalRequest != null) {
            throw new IllegalStateException("The request already has an approval request");
        }
        this.approvalRequest = Require.notNull(request, "approvalRequest");
    }

    public ApprovalRequestStatus approvalStatus() {
        return approvalRequest == null ? null : approvalRequest.getStatus();
    }

    public CampusServiceRequestStatus effectiveStatus() {
        return status.withApproval(approvalStatus());
    }

    public boolean synchronizeWithApproval() {
        CampusServiceRequestStatus effective = effectiveStatus();
        if (effective == status) {
            return false;
        }
        status = effective;
        return true;
    }

    public void cancel() {
        if (status != CampusServiceRequestStatus.SUBMITTED && status != CampusServiceRequestStatus.APPROVED) {
            throw new IllegalStateException("Only an open request can be cancelled");
        }
        status = CampusServiceRequestStatus.CANCELLED;
    }

    public void complete(Instant at) {
        if (!status.isBeingHandled()) {
            throw new IllegalStateException("Only an approved request can be completed");
        }
        status = CampusServiceRequestStatus.COMPLETED;
        completedAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
    }

    public void complete(Instant at, VaultDocument document) {
        complete(at);
        this.issuedDocument = Require.notNull(document, "document");
    }

    public boolean startProcessing(Instant at) {
        if (status == CampusServiceRequestStatus.IN_PROGRESS) {
            return false;
        }
        if (status != CampusServiceRequestStatus.APPROVED) {
            throw new IllegalStateException("Only an approved request can be processed");
        }
        status = CampusServiceRequestStatus.IN_PROGRESS;
        processingAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
        return true;
    }

    public boolean markReady(Instant at) {
        if (status == CampusServiceRequestStatus.READY) {
            return false;
        }
        if (status != CampusServiceRequestStatus.APPROVED && status != CampusServiceRequestStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an approved or processing request can be ready");
        }
        status = CampusServiceRequestStatus.READY;
        readyAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
        return true;
    }

    public Instant getProcessingAt() {
        return processingAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public VaultDocument getIssuedDocument() {
        return issuedDocument;
    }

    public Long getId() {
        return id;
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public CampusServiceDefinition getServiceDefinition() {
        return serviceDefinition;
    }

    public UserAccount getRequester() {
        return requester;
    }

    public String getSubject() {
        return subject;
    }

    public String getDetails() {
        return details;
    }

    public CampusServiceRequestStatus getStatus() {
        return status;
    }

    public ApprovalRequest getApprovalRequest() {
        return approvalRequest;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
