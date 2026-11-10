package com.stacc.backend.campus.services;

import java.time.Clock;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.campus.CampusNotifications;
import com.stacc.backend.campus.services.CampusServiceResponses.AdminCampusRequestDetail;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.documents.DocumentVaultService;
import com.stacc.backend.documents.VaultDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The campus office's progress on an accepted request (v0.9.7): being processed, ready (the requester is told once),
 * and completed with a document issued into the requester's vault. Each step locks the request row and first takes
 * any approval outcome into account through {@link CampusServiceRequestService#synchronize}. Nothing goes backwards.
 */
@Service
@Transactional
public class CampusRequestProgressService {

    static final String NOT_FOUND = "Service request not found.";
    static final String CANNOT_PROCESS = "Only an approved service request can be processed.";
    static final String CANNOT_READY = "Only an approved service request that is not finished can be marked ready.";
    static final String ALREADY_COMPLETED = "This request is already completed. A document can be issued only when completing it.";

    private final CampusServiceRequestRepository requests;
    private final CampusServiceRequestService requestService;
    private final CampusServiceQueryService queries;
    private final CampusNotifications notifications;
    private final DocumentVaultService documents;
    private final Clock clock;

    public CampusRequestProgressService(CampusServiceRequestRepository requests, CampusServiceRequestService requestService,
            CampusServiceQueryService queries, CampusNotifications notifications, DocumentVaultService documents, Clock clock) {
        this.requests = requests;
        this.requestService = requestService;
        this.queries = queries;
        this.notifications = notifications;
        this.documents = documents;
        this.clock = clock;
    }

    /** An approved request is being processed; repeating changes nothing. */
    public AdminCampusRequestDetail startProcessing(ApprovalActor admin, Long requestId) {
        CampusServiceRequest request = locked(requestId);
        refuseUnless(() -> request.startProcessing(clock.instant()), CANNOT_PROCESS);
        requests.saveAndFlush(request);
        return queries.adminRequest(admin, requestId);
    }

    /** An approved or processing request is ready; the requester is told the first time only. */
    public AdminCampusRequestDetail markReady(ApprovalActor admin, Long requestId) {
        CampusServiceRequest request = locked(requestId);
        if (refuseUnless(() -> request.markReady(clock.instant()), CANNOT_READY)) {
            requests.saveAndFlush(request);
            notifications.serviceReady(request);
        }
        return queries.adminRequest(admin, requestId);
    }

    /** Completes an accepted request with a document issued into the requester's vault, and tells the requester. */
    public AdminCampusRequestDetail completeWithDocument(ApprovalActor admin, Long requestId, String title, MultipartFile file) {
        CampusServiceRequest request = locked(requestId);
        if (request.getStatus() == CampusServiceRequestStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_COMPLETED);
        }
        if (!request.getStatus().isBeingHandled()) {
            throw new ApiException(HttpStatus.CONFLICT, CampusServiceRequestService.CANNOT_COMPLETE);
        }
        VaultDocument document = documents.issueFile(admin.accountId(), request.getRequester(), title, file);
        request.complete(clock.instant(), document);
        requests.saveAndFlush(request);
        notifications.serviceCompleted(request);
        return queries.adminRequest(admin, requestId);
    }

    private CampusServiceRequest locked(Long requestId) {
        CampusServiceRequest request = (requestId == null ? Optional.<CampusServiceRequest>empty() : requests.findByIdForUpdate(requestId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        requestService.synchronize(request);
        return request;
    }

    private static boolean refuseUnless(java.util.function.BooleanSupplier step, String refusal) {
        try {
            return step.getAsBoolean();
        } catch (IllegalStateException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refusal);
        }
    }
}
