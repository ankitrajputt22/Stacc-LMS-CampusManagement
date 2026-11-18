package com.stacc.backend.approval;

import java.time.Instant;

public final class ApprovalCommands {

    private ApprovalCommands() {
    }

    public record StartApprovalCommand(String workflowCode, Long requesterUserAccountId, String sourceType,
            String sourceId, String subject) {
    }

    public record ApprovalStartResult(Long approvalRequestId, ApprovalRequestStatus status) {
    }

    public record ApprovalRequestOutcome(Long approvalRequestId, String workflowCode, String sourceType, String sourceId,
            ApprovalRequestStatus status, Integer currentStepOrder, Instant submittedAt, Instant completedAt) {

        static ApprovalRequestOutcome of(ApprovalRequest request) {
            return new ApprovalRequestOutcome(request.getId(), request.getWorkflowDefinition().getCode(),
                    request.getSourceType(), request.getSourceId(), request.getStatus(), request.getCurrentStepOrder(),
                    request.getSubmittedAt(), request.getCompletedAt());
        }
    }

    public record CurrentApprovalStep(Long approvalRequestStepId, int stepOrder, String stepName, String requiredRoleCode) {
    }
}
