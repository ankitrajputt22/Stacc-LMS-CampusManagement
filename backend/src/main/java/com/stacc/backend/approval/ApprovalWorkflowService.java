package com.stacc.backend.approval;

import java.time.Clock;
import java.util.Locale;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalCommands.ApprovalRequestOutcome;
import com.stacc.backend.approval.ApprovalCommands.ApprovalStartResult;
import com.stacc.backend.approval.ApprovalCommands.StartApprovalCommand;
import com.stacc.backend.approval.ApprovalResponses.ApprovalDecisionResponse;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApprovalWorkflowService {

    static final String WORKFLOW_NOT_FOUND = "Approval workflow not found.";
    static final String WORKFLOW_INACTIVE = "This approval workflow is inactive and cannot start new requests.";
    static final String WORKFLOW_EMPTY = "This approval workflow has no steps.";
    static final String REQUESTER_NOT_FOUND = "The requester account does not exist.";
    static final String REQUEST_NOT_FOUND = "Approval request not found.";
    static final String TASK_NOT_FOUND = "Approval task not found.";
    static final String STEP_DECIDED = "This step has already been decided.";
    static final String STEP_NOT_ACTIVE = "This step is not waiting for a decision yet.";
    static final String COMMENT_REQUIRED = "A comment is required to reject.";
    public static final String NO_APPROVER = "Nobody can approve this request right now: the approver it needs is not set up. Contact the college administration.";

    private final ApprovalWorkflowDefinitionRepository workflows;
    private final ApprovalRequestRepository requests;
    private final ApprovalRequestStepRepository steps;
    private final ApprovalRequestHistoryRepository history;
    private final UserAccountRepository accounts;
    private final ApprovalAccessService access;
    private final ApprovalNotifications notifications;
    private final Clock clock;
    private final List<ApproverRelationshipResolver> relationshipResolvers;

    public ApprovalWorkflowService(ApprovalWorkflowDefinitionRepository workflows, ApprovalRequestRepository requests,
            ApprovalRequestStepRepository steps, ApprovalRequestHistoryRepository history,
            UserAccountRepository accounts, ApprovalAccessService access, ApprovalNotifications notifications,
            Clock clock, List<ApproverRelationshipResolver> relationshipResolvers) {
        this.workflows = workflows;
        this.requests = requests;
        this.steps = steps;
        this.history = history;
        this.accounts = accounts;
        this.access = access;
        this.notifications = notifications;
        this.clock = clock;
        this.relationshipResolvers = List.copyOf(relationshipResolvers);
    }

    public ApprovalStartResult startApproval(StartApprovalCommand command) {
        String code = command.workflowCode() == null ? "" : command.workflowCode().strip().toUpperCase(Locale.ROOT);
        ApprovalWorkflowDefinition workflow = workflows.findWithStepsByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, WORKFLOW_NOT_FOUND));
        if (!workflow.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, WORKFLOW_INACTIVE);
        }
        if (workflow.getSteps().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, WORKFLOW_EMPTY);
        }
        UserAccount requester = (command.requesterUserAccountId() == null ? Optional.<UserAccount>empty()
                : accounts.findById(command.requesterUserAccountId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, REQUESTER_NOT_FOUND));
        ApprovalRequest request;
        try {
            request = new ApprovalRequest(workflow, requester, command.sourceType(), command.sourceId(),
                    command.subject(), clock.instant());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The approval request is not valid: " + invalid.getMessage() + ".");
        }
        assignRelationships(workflow, request, requester);
        request = requests.saveAndFlush(request);
        ApprovalRequestStep first = request.currentStep().orElseThrow();
        record(request, requester, ApprovalRequestHistoryAction.REQUEST_CREATED, null, null);
        record(request, null, ApprovalRequestHistoryAction.STEP_ACTIVATED, first.getStepOrder(), null);
        notifications.actionRequired(request, first);
        return new ApprovalStartResult(request.getId(), request.getStatus());
    }

    private void assignRelationships(ApprovalWorkflowDefinition workflow, ApprovalRequest request, UserAccount requester) {
        for (ApprovalWorkflowStepDefinition definition : workflow.getSteps()) {
            ApproverRelationship relationship = definition.getApproverRelationship();
            if (relationship == null) {
                continue;
            }
            Long approverId = relationshipResolvers.stream().filter(resolver -> resolver.relationship() == relationship)
                    .findFirst().flatMap(resolver -> resolver.approverFor(requester.getId(), request.getSourceType(), request.getSourceId()))
                    .filter(id -> !id.equals(requester.getId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NO_APPROVER));
            ApprovalRequestStep step = request.getSteps().stream()
                    .filter(candidate -> candidate.getStepOrder() == definition.getStepOrder()).findFirst().orElseThrow();
            step.assign(relationship, accounts.getReferenceById(approverId));
        }
    }

    public ApprovalRequestOutcome cancelApproval(Long approvalRequestId, Long actorAccountId) {
        ApprovalRequest request = locked(approvalRequestId, REQUEST_NOT_FOUND);
        requirePending(request);
        UserAccount actor = actorAccountId == null ? null : accounts.findById(actorAccountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "The cancelling account does not exist."));
        request.cancel(clock.instant());
        requests.saveAndFlush(request);
        record(request, actor, ApprovalRequestHistoryAction.REQUEST_CANCELLED, null, null);
        notifications.cancelled(request, actorAccountId);
        return ApprovalRequestOutcome.of(request);
    }

    public boolean cancelApprovalIfPending(Long approvalRequestId, Long actorAccountId) {
        ApprovalRequest request = locked(approvalRequestId, REQUEST_NOT_FOUND);
        if (request.getStatus().isTerminal()) {
            return false;
        }
        cancelApproval(approvalRequestId, actorAccountId);
        return true;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApprovalDecisionResponse approveStep(ApprovalActor actor, Long approvalRequestStepId, String comment) {
        Decision decision = decision(actor, approvalRequestStepId);
        ApprovalRequest request = decision.request();
        ApprovalRequestStep step = decision.step();
        Optional<ApprovalRequestStep> next = request.approve(step, decision.approver(), comment, clock.instant());
        requests.saveAndFlush(request);
        record(request, decision.approver(), ApprovalRequestHistoryAction.STEP_APPROVED, step.getStepOrder(),
                step.getComment());
        if (next.isPresent()) {
            record(request, null, ApprovalRequestHistoryAction.STEP_ACTIVATED, next.get().getStepOrder(), null);
            notifications.actionRequired(request, next.get());
        } else {
            record(request, null, ApprovalRequestHistoryAction.REQUEST_APPROVED, null, null);
            notifications.approved(request);
        }
        return resultOf(request, step);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApprovalDecisionResponse rejectStep(ApprovalActor actor, Long approvalRequestStepId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, COMMENT_REQUIRED);
        }
        Decision decision = decision(actor, approvalRequestStepId);
        ApprovalRequest request = decision.request();
        ApprovalRequestStep step = decision.step();
        request.reject(step, decision.approver(), comment, clock.instant());
        requests.saveAndFlush(request);
        record(request, decision.approver(), ApprovalRequestHistoryAction.STEP_REJECTED, step.getStepOrder(),
                step.getComment());
        record(request, null, ApprovalRequestHistoryAction.REQUEST_REJECTED, null, null);
        notifications.rejected(request);
        return resultOf(request, step);
    }

    private record Decision(ApprovalRequest request, ApprovalRequestStep step, UserAccount approver) {
    }

    private Decision decision(ApprovalActor actor, Long approvalRequestStepId) {
        Long requestId = approvalRequestStepId == null ? null
                : steps.findRequestIdByStepId(approvalRequestStepId).orElse(null);
        ApprovalRequest request = locked(requestId, TASK_NOT_FOUND);
        ApprovalRequestStep step = request.getSteps().stream()
                .filter(candidate -> candidate.getId().equals(approvalRequestStepId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, TASK_NOT_FOUND));
        UserAccount approver = accounts.findById(actor.accountId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, ApprovalAccessService.CANNOT_DECIDE));
        access.requireDecider(actor, approver, request, step);
        requirePending(request);
        if (step.getStatus() != ApprovalRequestStepStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT,
                    step.getStatus() == ApprovalRequestStepStatus.WAITING ? STEP_NOT_ACTIVE : STEP_DECIDED);
        }
        return new Decision(request, step, approver);
    }

    private ApprovalRequest locked(Long requestId, String notFound) {
        if (requestId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, notFound);
        }
        return requests.findByIdForUpdate(requestId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, notFound));
    }

    private static void requirePending(ApprovalRequest request) {
        if (request.getStatus().isTerminal()) {
            throw new ApiException(HttpStatus.CONFLICT, "This approval request is already "
                    + request.getStatus().name().toLowerCase(Locale.ROOT) + " and cannot change.");
        }
    }

    private void record(ApprovalRequest request, UserAccount actor, ApprovalRequestHistoryAction action,
            Integer stepOrder, String comment) {
        history.saveAndFlush(new ApprovalRequestHistory(request, actor, action, stepOrder, comment));
    }

    private static ApprovalDecisionResponse resultOf(ApprovalRequest request, ApprovalRequestStep step) {
        return new ApprovalDecisionResponse(step.getId(), request.getId(), step.getStatus().name(),
                request.getStatus().name(), request.getCurrentStepOrder());
    }
}
