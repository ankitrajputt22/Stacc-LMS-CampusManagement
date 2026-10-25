package com.stacc.backend.approval;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
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
 * One step of an approval request, copied from the workflow when the request started: its number, name, and the code of
 * the role that decides it. Only the request changes it, and the decision records who made it and when.
 */
@Entity
@Table(name = "approval_request_steps")
public class ApprovalRequestStep {

    public static final int MAX_COMMENT_LENGTH = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_request_id", nullable = false, updatable = false)
    private ApprovalRequest approvalRequest;

    @Column(name = "step_order", nullable = false, updatable = false)
    private int stepOrder;

    @Column(name = "step_name", nullable = false, updatable = false, length = ApprovalWorkflowDefinition.MAX_NAME_LENGTH)
    private String stepName;

    @Column(name = "required_role_code", nullable = false, updatable = false, length = 50)
    private String requiredRoleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalRequestStepStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acted_by_user_account_id")
    private UserAccount actedBy;

    @Column(name = "acted_at")
    private Instant actedAt;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApprovalRequestStep() {
        // Required by JPA.
    }

    ApprovalRequestStep(ApprovalRequest approvalRequest, int stepOrder, String stepName, String requiredRoleCode,
            ApprovalRequestStepStatus status) {
        this.approvalRequest = Require.notNull(approvalRequest, "approvalRequest");
        this.stepOrder = stepOrder;
        this.stepName = Require.text(stepName, "stepName", ApprovalWorkflowDefinition.MAX_NAME_LENGTH);
        this.requiredRoleCode = Require.text(requiredRoleCode, "requiredRoleCode", 50);
        this.status = Require.notNull(status, "status");
    }

    void activate() {
        if (status != ApprovalRequestStepStatus.WAITING) {
            throw new IllegalStateException("Only a waiting step can become pending");
        }
        status = ApprovalRequestStepStatus.PENDING;
    }

    void decide(ApprovalRequestStepStatus decision, UserAccount actor, String comment, Instant at) {
        if (status != ApprovalRequestStepStatus.PENDING) {
            throw new IllegalStateException("Only a pending step can be decided");
        }
        this.status = decision;
        this.actedBy = Require.notNull(actor, "actor");
        this.actedAt = Require.notNull(at, "at");
        this.comment = Require.optionalText(comment, "comment", MAX_COMMENT_LENGTH);
    }

    void skip() {
        status = ApprovalRequestStepStatus.SKIPPED;
    }

    public Long getId() {
        return id;
    }

    public ApprovalRequest getApprovalRequest() {
        return approvalRequest;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public String getStepName() {
        return stepName;
    }

    public String getRequiredRoleCode() {
        return requiredRoleCode;
    }

    public ApprovalRequestStepStatus getStatus() {
        return status;
    }

    public UserAccount getActedBy() {
        return actedBy;
    }

    public Instant getActedAt() {
        return actedAt;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
