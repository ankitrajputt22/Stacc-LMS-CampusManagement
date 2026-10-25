package com.stacc.backend.approval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.stacc.backend.approval.ApprovalApiRequests.ApprovalWorkflowStepRequest;
import com.stacc.backend.approval.ApprovalApiRequests.CreateApprovalWorkflowRequest;
import com.stacc.backend.approval.ApprovalApiRequests.UpdateApprovalWorkflowRequest;
import com.stacc.backend.approval.ApprovalResponses.ApprovalWorkflowDetail;
import com.stacc.backend.approval.ApprovalWorkflowDefinition.StepSpec;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.role.RoleRepository;
import com.stacc.backend.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin configuration of approval workflows: create and update, with their ordered steps. A code is set once; a
 * workflow is made inactive instead of deleted. Each step names an existing role that can decide approval steps.
 * Changing the steps changes future requests only: running requests keep the steps they copied when they started.
 */
@Service
@Transactional
public class ApprovalWorkflowDefinitionService {

    static final String NOT_FOUND = "Approval workflow not found.";
    static final String CODE_TAKEN = "An approval workflow with this code already exists.";
    static final String BAD_CODE = "The code must be letters, digits, and underscores, starting with a letter.";

    private final ApprovalWorkflowDefinitionRepository workflows;
    private final RoleRepository roles;

    public ApprovalWorkflowDefinitionService(ApprovalWorkflowDefinitionRepository workflows, RoleRepository roles) {
        this.workflows = workflows;
        this.roles = roles;
    }

    public ApprovalWorkflowDetail create(CreateApprovalWorkflowRequest request) {
        String code;
        try {
            code = ApprovalWorkflowDefinition.normalizedCode(request.code());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, BAD_CODE);
        }
        if (workflows.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, CODE_TAKEN);
        }
        ApprovalWorkflowDefinition workflow = configured(
                () -> new ApprovalWorkflowDefinition(code, request.name(), request.description(), stepsOf(request.steps())));
        try {
            return ApprovalWorkflowDetail.of(workflows.saveAndFlush(workflow));
        } catch (DataIntegrityViolationException clash) {
            // Two requests raced for the same code; the unique constraint refused the second.
            throw new ApiException(HttpStatus.CONFLICT, CODE_TAKEN);
        }
    }

    /** Replaces the name, description, active flag, and steps in one transaction. The code never changes. */
    public ApprovalWorkflowDetail update(Long workflowId, UpdateApprovalWorkflowRequest request) {
        ApprovalWorkflowDefinition workflow = (workflowId == null ? null
                : workflows.findWithStepsById(workflowId).orElse(null));
        if (workflow == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        List<StepSpec> steps = stepsOf(request.steps());
        configured(() -> {
            workflow.update(request.name(), request.description(), request.active(), steps);
            return workflow;
        });
        try {
            return ApprovalWorkflowDetail.of(workflows.saveAndFlush(workflow));
        } catch (DataIntegrityViolationException clash) {
            throw new ApiException(HttpStatus.CONFLICT, "The workflow was changed at the same time. Try again.");
        }
    }

    /** The requested steps with their roles loaded on the server. Each role must exist and be able to decide steps. */
    private List<StepSpec> stepsOf(List<ApprovalWorkflowStepRequest> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > ApprovalWorkflowDefinition.MAX_STEPS) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "A workflow has 1 to " + ApprovalWorkflowDefinition.MAX_STEPS + " steps.");
        }
        List<StepSpec> steps = new ArrayList<>();
        for (ApprovalWorkflowStepRequest step : requested) {
            steps.add(new StepSpec(step.name(), roleOf(step.approverRoleCode())));
        }
        return steps;
    }

    private Role roleOf(String code) {
        String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        Role role;
        try {
            role = roles.findByName(RoleName.valueOf(normalized)).orElse(null);
        } catch (IllegalArgumentException unknown) {
            role = null;
        }
        if (role == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown approver role: " + normalized + ".");
        }
        // A step only its holders can decide would wait forever, so such a role is refused here.
        if (!role.hasPermission(ApprovalActor.TASK_DECIDE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "The " + normalized + " role cannot decide approval steps.");
        }
        return role;
    }

    private static ApprovalWorkflowDefinition configured(Supplier<ApprovalWorkflowDefinition> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The workflow is not valid: " + invalid.getMessage() + ".");
        }
    }
}
