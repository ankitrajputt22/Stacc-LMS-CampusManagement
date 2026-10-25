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

/**
 * One entry of an approval request's audit trail. The actor is the account that acted, or empty for a transition the
 * engine makes itself, such as activating the next step. Entries are append-only and never changed or deleted.
 */
@Entity
@Table(name = "approval_request_history")
public class ApprovalRequestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_request_id", nullable = false, updatable = false)
    private ApprovalRequest approvalRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_account_id", updatable = false)
    private UserAccount actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private ApprovalRequestHistoryAction action;

    @Column(name = "step_order", updatable = false)
    private Integer stepOrder;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApprovalRequestHistory() {
        // Required by JPA.
    }

    public ApprovalRequestHistory(ApprovalRequest approvalRequest, UserAccount actor, ApprovalRequestHistoryAction action,
            Integer stepOrder, String comment) {
        this.approvalRequest = Require.notNull(approvalRequest, "approvalRequest");
        this.actor = actor;
        this.action = Require.notNull(action, "action");
        this.stepOrder = stepOrder;
        this.comment = Require.optionalText(comment, "comment", ApprovalRequestStep.MAX_COMMENT_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public ApprovalRequest getApprovalRequest() {
        return approvalRequest;
    }

    public UserAccount getActor() {
        return actor;
    }

    public ApprovalRequestHistoryAction getAction() {
        return action;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
