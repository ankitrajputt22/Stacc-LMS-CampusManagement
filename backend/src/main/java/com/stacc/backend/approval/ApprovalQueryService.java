package com.stacc.backend.approval;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalCommands.ApprovalRequestOutcome;
import com.stacc.backend.approval.ApprovalCommands.CurrentApprovalStep;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestDetail;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestPage;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestSummary;
import com.stacc.backend.approval.ApprovalResponses.ApprovalTaskPage;
import com.stacc.backend.approval.ApprovalResponses.ApprovalTaskSummary;
import com.stacc.backend.approval.ApprovalResponses.ApprovalWorkflowDetail;
import com.stacc.backend.approval.ApprovalResponses.ApprovalWorkflowSummary;
import com.stacc.backend.common.error.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading approvals: a requester's own requests, an approver's task queue, the admin workflow list, and status lookups
 * for business modules. It only reads. A request the caller may not see answers the same 404 as a missing one.
 */
@Service
@Transactional(readOnly = true)
public class ApprovalQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final ApprovalWorkflowDefinitionRepository workflows;
    private final ApprovalRequestRepository requests;
    private final ApprovalRequestStepRepository steps;
    private final ApprovalRequestHistoryRepository history;
    private final ApprovalAccessService access;

    public ApprovalQueryService(ApprovalWorkflowDefinitionRepository workflows, ApprovalRequestRepository requests,
            ApprovalRequestStepRepository steps, ApprovalRequestHistoryRepository history, ApprovalAccessService access) {
        this.workflows = workflows;
        this.requests = requests;
        this.steps = steps;
        this.history = history;
        this.access = access;
    }

    /** The signed-in account's own requests, newest first. */
    public ApprovalRequestPage ownRequests(ApprovalActor actor, ApprovalRequestStatus status, int page, int size) {
        PageRequest request = pageOf(page, size);
        Page<ApprovalRequest> rows = requests.findOwn(actor.accountId(), status, request);
        return new ApprovalRequestPage(rows.getContent().stream().map(ApprovalRequestSummary::of).toList(),
                request.getPageNumber(), request.getPageSize(), rows.getTotalElements(), rows.getTotalPages());
    }

    /** One of the signed-in account's own requests with its steps and history. Anyone else's answers 404. */
    public ApprovalRequestDetail ownRequest(ApprovalActor actor, Long approvalRequestId) {
        ApprovalRequest request = approvalRequestId == null ? null
                : requests.findWithContextById(approvalRequestId).orElse(null);
        if (request == null || !access.canView(actor, request)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ApprovalWorkflowService.REQUEST_NOT_FOUND);
        }
        return ApprovalRequestDetail.of(request, steps.findAllWithActorByRequestId(request.getId()),
                history.findAllWithActorByRequestId(request.getId()));
    }

    /**
     * The steps the signed-in approver can decide now: PENDING steps that require one of the roles in the access token,
     * never anyone's own requests, oldest first. There is no admin pass: ADMIN sees ADMIN steps only.
     */
    public ApprovalTaskPage tasks(ApprovalActor actor, int page, int size) {
        if (!actor.taskView()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot see approval tasks.");
        }
        PageRequest request = pageOf(page, size);
        if (actor.roleCodes().isEmpty()) {
            return new ApprovalTaskPage(List.of(), request.getPageNumber(), request.getPageSize(), 0, 0);
        }
        Page<ApprovalRequestStep> rows = steps.findTasks(actor.roleCodes(), actor.accountId(), request);
        return new ApprovalTaskPage(rows.getContent().stream().map(ApprovalTaskSummary::of).toList(),
                request.getPageNumber(), request.getPageSize(), rows.getTotalElements(), rows.getTotalPages());
    }

    public List<ApprovalWorkflowSummary> workflows() {
        return workflows.findAllWithSteps().stream().map(ApprovalWorkflowSummary::of).toList();
    }

    public ApprovalWorkflowDetail workflow(Long workflowId) {
        return (workflowId == null ? Optional.<ApprovalWorkflowDefinition>empty() : workflows.findWithStepsById(workflowId))
                .map(ApprovalWorkflowDetail::of)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApprovalWorkflowDefinitionService.NOT_FOUND));
    }

    // --- For business modules. These are Java calls only; the module checks its own access rules first.

    /** Where one approval request is. */
    public Optional<ApprovalRequestOutcome> requestStatus(Long approvalRequestId) {
        return (approvalRequestId == null ? Optional.<ApprovalRequest>empty() : requests.findWithContextById(approvalRequestId))
                .map(ApprovalRequestOutcome::of);
    }

    /**
     * One request with its steps and history, for the module that owns its record and has checked its own access rules,
     * such as a hostel admin's review page. It is never a route of its own.
     */
    public Optional<ApprovalRequestDetail> requestDetail(Long approvalRequestId) {
        return (approvalRequestId == null ? Optional.<ApprovalRequest>empty() : requests.findWithContextById(approvalRequestId))
                .map(request -> ApprovalRequestDetail.of(request, steps.findAllWithActorByRequestId(request.getId()),
                        history.findAllWithActorByRequestId(request.getId())));
    }

    /** The step of a PENDING request that can be decided now, with its ID for the task routes. */
    public Optional<CurrentApprovalStep> currentStep(Long approvalRequestId) {
        return (approvalRequestId == null ? Optional.<ApprovalRequest>empty() : requests.findWithContextById(approvalRequestId))
                .flatMap(ApprovalRequest::currentStep)
                .map(step -> new CurrentApprovalStep(step.getId(), step.getStepOrder(), step.getStepName(),
                        step.getRequiredRoleCode()));
    }

    /** Every approval request for one business record, oldest first. */
    public List<ApprovalRequestOutcome> requestsForSource(String sourceType, String sourceId) {
        return requests.findAllForSource(sourceType, sourceId).stream().map(ApprovalRequestOutcome::of).toList();
    }

    /**
     * Whether a workflow with this code exists, is active, and has steps, so that {@code startApproval} can use it. A
     * business module checks this when it is configured to start approvals with the workflow.
     */
    public boolean isStartableWorkflow(String workflowCode) {
        String code = workflowCode == null ? "" : workflowCode.strip().toUpperCase(Locale.ROOT);
        return !code.isEmpty() && workflows.findWithStepsByCode(code)
                .filter(workflow -> workflow.isActive() && !workflow.getSteps().isEmpty())
                .isPresent();
    }

    /**
     * Whether the record already has a PENDING request in this workflow. The engine allows several requests per record,
     * such as a second round after a rejection; the calling module decides whether to start another.
     */
    public boolean hasPendingApproval(String sourceType, String sourceId, String workflowCode) {
        String code = workflowCode == null ? "" : workflowCode.strip().toUpperCase(Locale.ROOT);
        return requests.existsPendingForSource(sourceType, sourceId, code);
    }

    private static PageRequest pageOf(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The page must be 0 or more.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size);
    }
}
