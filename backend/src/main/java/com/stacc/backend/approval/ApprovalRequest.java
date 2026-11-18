package com.stacc.backend.approval;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    public static final int MAX_SOURCE_TYPE_LENGTH = 80;
    public static final int MAX_SOURCE_ID_LENGTH = 100;
    public static final int MAX_SUBJECT_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false, updatable = false)
    private ApprovalWorkflowDefinition workflowDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_account_id", nullable = false, updatable = false)
    private UserAccount requester;

    @Column(name = "source_type", nullable = false, updatable = false, length = MAX_SOURCE_TYPE_LENGTH)
    private String sourceType;

    @Column(name = "source_id", nullable = false, updatable = false, length = MAX_SOURCE_ID_LENGTH)
    private String sourceId;

    @Column(nullable = false, updatable = false, length = MAX_SUBJECT_LENGTH)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalRequestStatus status = ApprovalRequestStatus.PENDING;

    @Column(name = "current_step_order")
    private Integer currentStepOrder;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "approvalRequest", cascade = CascadeType.PERSIST)
    @OrderBy("stepOrder ASC")
    private List<ApprovalRequestStep> steps = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApprovalRequest() {
    }

    public ApprovalRequest(ApprovalWorkflowDefinition workflowDefinition, UserAccount requester, String sourceType,
            String sourceId, String subject, Instant submittedAt) {
        this.workflowDefinition = Require.notNull(workflowDefinition, "workflowDefinition");
        this.requester = Require.notNull(requester, "requester");
        this.sourceType = Require.text(sourceType, "sourceType", MAX_SOURCE_TYPE_LENGTH);
        this.sourceId = Require.text(sourceId, "sourceId", MAX_SOURCE_ID_LENGTH);
        this.subject = Require.text(subject, "subject", MAX_SUBJECT_LENGTH);
        this.submittedAt = micros(Require.notNull(submittedAt, "submittedAt"));
        if (workflowDefinition.getSteps().isEmpty()) {
            throw new IllegalArgumentException("the workflow has no steps");
        }
        for (ApprovalWorkflowStepDefinition definition : workflowDefinition.getSteps()) {
            steps.add(new ApprovalRequestStep(this, definition.getStepOrder(), definition.getName(),
                    definition.getApproverRole().getName().name(),
                    definition.getStepOrder() == 1 ? ApprovalRequestStepStatus.PENDING : ApprovalRequestStepStatus.WAITING));
        }
        this.currentStepOrder = 1;
    }

    public Optional<ApprovalRequestStep> currentStep() {
        if (status != ApprovalRequestStatus.PENDING || currentStepOrder == null) {
            return Optional.empty();
        }
        return steps.stream().filter(step -> step.getStepOrder() == currentStepOrder).findFirst();
    }

    public Optional<ApprovalRequestStep> approve(ApprovalRequestStep step, UserAccount actor, String comment, Instant at) {
        requireCurrent(step);
        Instant now = micros(at);
        step.decide(ApprovalRequestStepStatus.APPROVED, actor, comment, now);
        Optional<ApprovalRequestStep> next = steps.stream()
                .filter(candidate -> candidate.getStepOrder() == step.getStepOrder() + 1)
                .findFirst();
        if (next.isPresent()) {
            next.get().activate();
            currentStepOrder = next.get().getStepOrder();
        } else {
            finish(ApprovalRequestStatus.APPROVED, now);
        }
        return next;
    }

    public void reject(ApprovalRequestStep step, UserAccount actor, String comment, Instant at) {
        requireCurrent(step);
        Instant now = micros(at);
        step.decide(ApprovalRequestStepStatus.REJECTED, actor, comment, now);
        finish(ApprovalRequestStatus.REJECTED, now);
    }

    public void cancel(Instant at) {
        if (status != ApprovalRequestStatus.PENDING) {
            throw new IllegalStateException("Only a pending request can be cancelled");
        }
        finish(ApprovalRequestStatus.CANCELLED, micros(at));
    }

    private void finish(ApprovalRequestStatus terminal, Instant at) {
        for (ApprovalRequestStep step : steps) {
            if (step.getStatus() == ApprovalRequestStepStatus.PENDING || step.getStatus() == ApprovalRequestStepStatus.WAITING) {
                step.skip();
            }
        }
        this.status = terminal;
        this.currentStepOrder = null;
        this.completedAt = at;
    }

    private void requireCurrent(ApprovalRequestStep step) {
        if (step == null || currentStep().orElse(null) != step) {
            throw new IllegalStateException("Only the current step of a pending request can be decided");
        }
    }

    private static Instant micros(Instant at) {
        return at.truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isPending() {
        return status == ApprovalRequestStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public ApprovalWorkflowDefinition getWorkflowDefinition() {
        return workflowDefinition;
    }

    public UserAccount getRequester() {
        return requester;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSubject() {
        return subject;
    }

    public ApprovalRequestStatus getStatus() {
        return status;
    }

    public Integer getCurrentStepOrder() {
        return currentStepOrder;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<ApprovalRequestStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
