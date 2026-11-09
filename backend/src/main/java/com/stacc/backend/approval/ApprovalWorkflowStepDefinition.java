package com.stacc.backend.approval;

import java.time.Instant;

import com.stacc.backend.auth.role.Role;
import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** One ordered step of a workflow: its number, a name, and the role whose holders decide it. */
@Entity
@Table(name = "approval_workflow_step_definitions")
public class ApprovalWorkflowStepDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false, updatable = false)
    private ApprovalWorkflowDefinition workflowDefinition;

    @Column(name = "step_order", nullable = false, updatable = false)
    private int stepOrder;

    @Column(nullable = false, length = ApprovalWorkflowDefinition.MAX_NAME_LENGTH)
    private String name;

    // The role is shared reference data: it is never created, changed, or deleted through a workflow.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_role_id", nullable = false)
    private Role approverRole;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "approver_relationship", length = 40)
    private ApproverRelationship approverRelationship;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApprovalWorkflowStepDefinition() {
        // Required by JPA.
    }

    ApprovalWorkflowStepDefinition(ApprovalWorkflowDefinition workflowDefinition, int stepOrder, String name,
            Role approverRole, ApproverRelationship relationship) {
        this.workflowDefinition = Require.notNull(workflowDefinition, "workflowDefinition");
        this.stepOrder = stepOrder;
        change(name, approverRole, relationship);
    }

    void change(String name, Role approverRole, ApproverRelationship relationship) {
        this.name = Require.text(name, "step name", ApprovalWorkflowDefinition.MAX_NAME_LENGTH);
        this.approverRole = Require.notNull(approverRole, "approverRole");
        if (relationship != null && !relationship.roleCode().equals(approverRole.getName().name())) {
            throw new IllegalArgumentException("a " + relationship + " step needs the " + relationship.roleCode() + " role");
        }
        this.approverRelationship = relationship;
    }

    /** The relationship that names this step's approver, or null for any holder of the role. */
    public ApproverRelationship getApproverRelationship() {
        return approverRelationship;
    }

    public Long getId() {
        return id;
    }

    public ApprovalWorkflowDefinition getWorkflowDefinition() {
        return workflowDefinition;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public String getName() {
        return name;
    }

    public Role getApproverRole() {
        return approverRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
