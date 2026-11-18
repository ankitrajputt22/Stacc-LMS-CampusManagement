package com.stacc.backend.campus.services;

import java.time.Clock;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalCommands.ApprovalStartResult;
import com.stacc.backend.approval.ApprovalCommands.StartApprovalCommand;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalRequestRepository;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.campus.CampusActor;
import com.stacc.backend.campus.CampusNotifications;
import com.stacc.backend.campus.services.CampusServiceInputs.CreateCampusServiceRequestInput;
import com.stacc.backend.campus.services.CampusServiceResponses.AdminCampusRequestDetail;
import com.stacc.backend.campus.services.CampusServiceResponses.CampusRequestDetail;
import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CampusServiceRequestService {

    public static final String SOURCE_TYPE = "CAMPUS_SERVICE_REQUEST";
    static final String NOT_FOUND = "Service request not found.";
    static final String SERVICE_NOT_FOUND = "Campus service not found.";
    static final String SERVICE_INACTIVE = "This campus service is not accepting new requests.";
    static final String SERVICE_UNAVAILABLE = "This campus service is not available to your account.";
    static final String APPROVAL_UNAVAILABLE = "This campus service cannot take requests right now. Please try again later.";
    static final String CANNOT_CANCEL = "This service request can no longer be cancelled.";
    static final String CANNOT_COMPLETE = "Only an approved service request can be completed.";

    private final CampusServiceRequestRepository requests;
    private final CampusServiceDefinitionRepository services;
    private final UserAccountRepository accounts;
    private final ApprovalWorkflowService approvals;
    private final ApprovalQueryService approvalQueries;
    private final ApprovalRequestRepository approvalRequests;
    private final CampusServiceQueryService queries;
    private final CampusNotifications notifications;
    private final Clock clock;

    public CampusServiceRequestService(CampusServiceRequestRepository requests,
            CampusServiceDefinitionRepository services, UserAccountRepository accounts,
            ApprovalWorkflowService approvals, ApprovalQueryService approvalQueries,
            ApprovalRequestRepository approvalRequests, CampusServiceQueryService queries,
            CampusNotifications notifications, Clock clock) {
        this.requests = requests;
        this.services = services;
        this.accounts = accounts;
        this.approvals = approvals;
        this.approvalQueries = approvalQueries;
        this.approvalRequests = approvalRequests;
        this.queries = queries;
        this.notifications = notifications;
        this.clock = clock;
    }

    public CampusRequestDetail create(CampusActor actor, CreateCampusServiceRequestInput input) {
        CampusServiceDefinition service = (input.serviceId() == null ? Optional.<CampusServiceDefinition>empty()
                : services.findById(input.serviceId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SERVICE_NOT_FOUND));
        if (!service.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, SERVICE_INACTIVE);
        }
        if (!service.isAvailableTo(actor.roleCodes())) {
            throw new ApiException(HttpStatus.CONFLICT, SERVICE_UNAVAILABLE);
        }
        if (service.isApprovalRequired() && !approvalQueries.isStartableWorkflow(service.getApprovalWorkflowCode())) {
            throw new ApiException(HttpStatus.CONFLICT, APPROVAL_UNAVAILABLE);
        }
        UserAccount requester = (actor.accountId() == null ? Optional.<UserAccount>empty()
                : accounts.findById(actor.accountId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));
        CampusServiceRequest request;
        try {
            request = new CampusServiceRequest(service, requester, input.subject(), input.details(), clock.instant());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The request is not valid: " + invalid.getMessage() + ".");
        }
        request = requests.saveAndFlush(request);
        request.assignNumber();
        if (service.isApprovalRequired()) {
            ApprovalStartResult started;
            try {
                started = approvals.startApproval(new StartApprovalCommand(service.getApprovalWorkflowCode(),
                        requester.getId(), SOURCE_TYPE, String.valueOf(request.getId()),
                        "Campus service request " + request.getRequestNumber() + ": " + service.getName()));
            } catch (ApiException unavailable) {
                throw new ApiException(HttpStatus.CONFLICT, APPROVAL_UNAVAILABLE);
            }
            request.linkApproval(approvalRequests.getReferenceById(started.approvalRequestId()));
        }
        requests.saveAndFlush(request);
        return queries.ownRequest(requester.getId(), request.getId());
    }

    public CampusRequestDetail cancel(Long accountId, Long requestId) {
        CampusServiceRequest request = lockedOwn(accountId, requestId);
        if (request.getStatus() == CampusServiceRequestStatus.SUBMITTED && request.getApprovalRequest() != null) {
            boolean cancelledNow = approvals.cancelApprovalIfPending(request.getApprovalRequest().getId(), accountId);
            request.synchronizeWithApproval();
            if (cancelledNow) {
                requests.saveAndFlush(request);
                return queries.ownRequest(accountId, requestId);
            }
        }
        if (request.getStatus().isTerminal()) {
            throw new ApiException(HttpStatus.CONFLICT, CANNOT_CANCEL);
        }
        request.cancel();
        requests.saveAndFlush(request);
        return queries.ownRequest(accountId, requestId);
    }

    public AdminCampusRequestDetail complete(ApprovalActor admin, Long requestId) {
        CampusServiceRequest request = (requestId == null ? Optional.<CampusServiceRequest>empty()
                : requests.findByIdForUpdate(requestId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        synchronize(request);
        if (request.getStatus() == CampusServiceRequestStatus.COMPLETED) {
            return queries.adminRequest(admin, requestId);
        }
        if (!request.getStatus().isBeingHandled()) {
            throw new ApiException(HttpStatus.CONFLICT, CANNOT_COMPLETE);
        }
        request.complete(clock.instant());
        requests.saveAndFlush(request);
        notifications.serviceCompleted(request);
        return queries.adminRequest(admin, requestId);
    }

    void synchronize(CampusServiceRequest request) {
        if (request.synchronizeWithApproval()) {
            requests.saveAndFlush(request);
        }
    }

    private CampusServiceRequest lockedOwn(Long accountId, Long requestId) {
        CampusServiceRequest request = (requestId == null ? Optional.<CampusServiceRequest>empty()
                : requests.findByIdForUpdate(requestId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (!request.getRequester().getId().equals(accountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return request;
    }
}
