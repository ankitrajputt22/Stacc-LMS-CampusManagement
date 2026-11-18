package com.stacc.backend.campus.services;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.approval.ApprovalCommands.CurrentApprovalStep;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestDetail;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestStepResponse;
import com.stacc.backend.campus.services.CampusServiceResponses.AdminCampusRequestDetail;
import com.stacc.backend.campus.services.CampusServiceResponses.AdminCampusRequestPage;
import com.stacc.backend.campus.services.CampusServiceResponses.AdminCampusRequestSummary;
import com.stacc.backend.campus.services.CampusServiceResponses.CampusRequestDetail;
import com.stacc.backend.campus.services.CampusServiceResponses.CampusRequestPage;
import com.stacc.backend.campus.services.CampusServiceResponses.CampusRequestSummary;
import com.stacc.backend.common.error.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CampusServiceQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final CampusServiceRequestRepository requests;
    private final ApprovalQueryService approvals;

    public CampusServiceQueryService(CampusServiceRequestRepository requests, ApprovalQueryService approvals) {
        this.requests = requests;
        this.approvals = approvals;
    }

    public CampusRequestPage ownRequests(Long accountId, CampusServiceRequestStatus status, Long serviceId, int page,
            int size) {
        PageRequest request = pageOf(page, size);
        Page<CampusServiceRequest> rows = requests.findOwnPage(accountId, serviceId, status,
                status == null ? null : status.approvalEquivalent(), request);
        return new CampusRequestPage(rows.getContent().stream().map(CampusRequestSummary::of).toList(),
                request.getPageNumber(), request.getPageSize(), rows.getTotalElements(), rows.getTotalPages());
    }

    public CampusRequestDetail ownRequest(Long accountId, Long requestId) {
        CampusServiceRequest request = loaded(requestId);
        if (!request.getRequester().getId().equals(accountId)) {
            throw notFound();
        }
        ApprovalRequestDetail approval = approvalOf(request);
        CampusServiceRequestStatus status = request.effectiveStatus();
        CampusServiceDefinition service = request.getServiceDefinition();
        return new CampusRequestDetail(request.getId(), request.getRequestNumber(), service.getId(), service.getCode(),
                service.getName(), request.getSubject(), request.getDetails(), status.name(),
                CampusServiceResponses.nameOf(request.approvalStatus()), rejectionComment(approval),
                status == CampusServiceRequestStatus.SUBMITTED || status == CampusServiceRequestStatus.APPROVED,
                request.getSubmittedAt(), request.getCompletedAt(), approval == null ? List.of() : approval.steps(),
                request.getProcessingAt(), request.getReadyAt(), documentTitle(request));
    }

    public AdminCampusRequestPage adminRequests(Long serviceId, CampusServiceRequestStatus status,
            String requesterLoginId, int page, int size) {
        PageRequest request = pageOf(page, size);
        String loginId = requesterLoginId == null || requesterLoginId.isBlank() ? null : requesterLoginId.strip();
        Page<CampusServiceRequest> rows = requests.findQueue(serviceId, loginId, status,
                status == null ? null : status.approvalEquivalent(), request);
        return new AdminCampusRequestPage(rows.getContent().stream().map(AdminCampusRequestSummary::of).toList(),
                request.getPageNumber(), request.getPageSize(), rows.getTotalElements(), rows.getTotalPages());
    }

    public AdminCampusRequestDetail adminRequest(ApprovalActor actor, Long requestId) {
        CampusServiceRequest request = loaded(requestId);
        ApprovalRequestDetail approval = approvalOf(request);
        CurrentApprovalStep current = request.getApprovalRequest() == null ? null
                : approvals.currentStep(request.getApprovalRequest().getId()).orElse(null);
        Long requesterId = request.getRequester().getId();
        boolean canDecide = current != null && actor.taskDecide() && actor.roleCodes().contains(current.requiredRoleCode())
                && !requesterId.equals(actor.accountId());
        CampusServiceRequestStatus status = request.effectiveStatus();
        CampusServiceDefinition service = request.getServiceDefinition();
        return new AdminCampusRequestDetail(request.getId(), request.getRequestNumber(),
                request.getRequester().getLoginId(), service.getId(), service.getCode(), service.getName(),
                request.getSubject(), request.getDetails(), status.name(),
                CampusServiceResponses.nameOf(request.approvalStatus()), rejectionComment(approval),
                request.getSubmittedAt(), request.getCompletedAt(), approval == null ? List.of() : approval.steps(),
                approval == null ? List.of() : approval.history(), current, canDecide,
                status.isBeingHandled(), request.getProcessingAt(), request.getReadyAt(), documentTitle(request),
                status == CampusServiceRequestStatus.APPROVED,
                status == CampusServiceRequestStatus.APPROVED || status == CampusServiceRequestStatus.IN_PROGRESS);
    }

    private static String documentTitle(CampusServiceRequest request) {
        return request.getIssuedDocument() == null ? null : request.getIssuedDocument().getTitle();
    }

    private CampusServiceRequest loaded(Long requestId) {
        return (requestId == null ? Optional.<CampusServiceRequest>empty() : requests.findWithContextById(requestId))
                .orElseThrow(CampusServiceQueryService::notFound);
    }

    private ApprovalRequestDetail approvalOf(CampusServiceRequest request) {
        return request.getApprovalRequest() == null ? null
                : approvals.requestDetail(request.getApprovalRequest().getId()).orElse(null);
    }

    private static String rejectionComment(ApprovalRequestDetail approval) {
        if (approval == null) {
            return null;
        }
        return approval.steps().stream()
                .filter(step -> "REJECTED".equals(step.status()))
                .map(ApprovalRequestStepResponse::comment)
                .findFirst()
                .orElse(null);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, CampusServiceRequestService.NOT_FOUND);
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
