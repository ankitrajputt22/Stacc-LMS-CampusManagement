package com.stacc.backend.approval;

import java.time.Clock;
import java.util.Locale;
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

/**
 * The approval engine: starting and cancelling requests for business modules, and approving and rejecting steps for
 * approvers. Every real transition writes its history entries here, in the same transaction, and tells the people
 * concerned in their Inbox. Every change locks the request row first, so of two decisions on the same step exactly one
 * wins and the other gets a 409. The engine never changes the business record a request is about.
 */
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

    private final ApprovalWorkflowDefinitionRepository workflows;
    private final ApprovalRequestRepository requests;
    private final ApprovalRequestStepRepository steps;
    private final ApprovalRequestHistoryRepository history;
    private final UserAccountRepository accounts;
    private final ApprovalAccessService access;
    private final ApprovalNotifications notifications;
    private final Clock clock;

    public ApprovalWorkflowService(ApprovalWorkflowDefinitionRepository workflows, ApprovalRequestRepository requests,
            ApprovalRequestStepRepository steps, ApprovalRequestHistoryRepository history,
            UserAccountRepository accounts, ApprovalAccessService access, ApprovalNotifications notifications,
            Clock clock) {
        this.workflows = workflows;
        this.requests = requests;
        this.steps = steps;
        this.history = history;
        this.accounts = accounts;
        this.access = access;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Starts an approval for a business record, on behalf of the requester the calling module has authenticated. The
     * workflow must exist and be active. Its steps are copied: the first is PENDING and its approvers are told, the
     * others wait. Whether the record may be submitted at all, and whether a second request makes sense, is the calling
     * module's decision; {@link ApprovalQueryService#hasPendingApproval} helps with the latter.
     */
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
        request = requests.saveAndFlush(request);
        ApprovalRequestStep first = request.currentStep().orElseThrow();
        record(request, requester, ApprovalRequestHistoryAction.REQUEST_CREATED, null, null);
        record(request, null, ApprovalRequestHistoryAction.STEP_ACTIVATED, first.getStepOrder(), null);
        notifications.actionRequired(request, first);
        return new ApprovalStartResult(request.getId(), request.getStatus());
    }

    /**
     * Cancels a PENDING request for the business module that owns it, which decides whether cancelling is allowed. The
     * current and waiting steps are SKIPPED; decided steps stay. {@code actorAccountId} is the account that cancelled,
     * or null when the module cancels on its own.
     */
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

    /**
     * Cancels the request if it is still PENDING, and otherwise changes nothing. Returns whether it cancelled. The request
     * row is locked before its status is read, so a decision made at the same moment is never overwritten; a business
     * module whose own state follows the outcome calls this instead of reading the status first and then
     * {@link #cancelApproval}.
     */
    public boolean cancelApprovalIfPending(Long approvalRequestId, Long actorAccountId) {
        ApprovalRequest request = locked(approvalRequestId, REQUEST_NOT_FOUND);
        if (request.getStatus().isTerminal()) {
            return false;
        }
        cancelApproval(approvalRequestId, actorAccountId);
        return true;
    }

    /**
     * Approves the current step as the signed-in approver. The next step becomes PENDING and its approvers are told, or,
     * after the last step, the request is APPROVED and the requester is told.
     */
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

    /** Rejects the current step as the signed-in approver, with a reason: the request is REJECTED and later steps SKIPPED. */
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

    /**
     * Finds the step, locks its request, and checks the decision: 404 for an unknown step, 403 for an actor who may not
     * decide it, and 409 for a request or step that is no longer waiting for this decision.
     */
    private Decision decision(ApprovalActor actor, Long approvalRequestStepId) {
        Long requestId = approvalRequestStepId == null ? null
                : steps.findRequestIdByStepId(approvalRequestStepId).orElse(null);
        // The request is locked before its steps are read. Decisions run at READ COMMITTED, so once the lock is held
        // every read sees what an earlier decision committed, not a snapshot taken by the lookup above.
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

    // The one place history is written, so no transition can forget it.
    private void record(ApprovalRequest request, UserAccount actor, ApprovalRequestHistoryAction action,
            Integer stepOrder, String comment) {
        history.saveAndFlush(new ApprovalRequestHistory(request, actor, action, stepOrder, comment));
    }

    private static ApprovalDecisionResponse resultOf(ApprovalRequest request, ApprovalRequestStep step) {
        return new ApprovalDecisionResponse(step.getId(), request.getId(), step.getStatus().name(),
                request.getStatus().name(), request.getCurrentStepOrder());
    }
}
