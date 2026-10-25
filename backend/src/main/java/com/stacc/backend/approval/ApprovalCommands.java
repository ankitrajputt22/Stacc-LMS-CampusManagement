package com.stacc.backend.approval;

import java.time.Instant;

/**
 * The internal Java API of the approval engine for business modules. There is no REST route to start or cancel a
 * request: the owning module decides when its record needs approval and calls the engine with the requester it has
 * authenticated itself.
 */
public final class ApprovalCommands {

    private ApprovalCommands() {
    }

    /**
     * Starts an approval. {@code sourceType} and {@code sourceId} name the business record loosely, such as
     * HOSTEL_APPLICATION and its ID; {@code subject} is a short text to read in lists. No business data is stored.
     */
    public record StartApprovalCommand(String workflowCode, Long requesterUserAccountId, String sourceType,
            String sourceId, String subject) {
    }

    public record ApprovalStartResult(Long approvalRequestId, ApprovalRequestStatus status) {
    }

    /** Where an approval request is, for the module that started it. */
    public record ApprovalRequestOutcome(Long approvalRequestId, String workflowCode, String sourceType, String sourceId,
            ApprovalRequestStatus status, Integer currentStepOrder, Instant submittedAt, Instant completedAt) {

        static ApprovalRequestOutcome of(ApprovalRequest request) {
            return new ApprovalRequestOutcome(request.getId(), request.getWorkflowDefinition().getCode(),
                    request.getSourceType(), request.getSourceId(), request.getStatus(), request.getCurrentStepOrder(),
                    request.getSubmittedAt(), request.getCompletedAt());
        }
    }
}
