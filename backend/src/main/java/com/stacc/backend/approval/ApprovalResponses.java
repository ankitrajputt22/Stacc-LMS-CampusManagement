package com.stacc.backend.approval;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.account.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The approval engine as clients see it. People appear by login ID only, never by account ID, and no entity or
 * business record is sent as it is.
 */
public final class ApprovalResponses {

    private ApprovalResponses() {
    }

    private static String loginIdOf(UserAccount account) {
        return account == null ? null : account.getLoginId();
    }

    public record ApprovalWorkflowStepResponse(
            @Schema(example = "1") int stepOrder,
            @Schema(example = "Faculty review") String name,
            @Schema(example = "FACULTY") String approverRoleCode) {

        static ApprovalWorkflowStepResponse of(ApprovalWorkflowStepDefinition step) {
            return new ApprovalWorkflowStepResponse(step.getStepOrder(), step.getName(),
                    step.getApproverRole().getName().name());
        }
    }

    public record ApprovalWorkflowSummary(
            @Schema(example = "4") Long workflowId,
            @Schema(example = "HOSTEL_APPLICATION") String code,
            String name,
            @Schema(nullable = true) String description,
            boolean active,
            @Schema(example = "2") int stepCount,
            Instant createdAt,
            Instant updatedAt) {

        static ApprovalWorkflowSummary of(ApprovalWorkflowDefinition workflow) {
            return new ApprovalWorkflowSummary(workflow.getId(), workflow.getCode(), workflow.getName(),
                    workflow.getDescription(), workflow.isActive(), workflow.getSteps().size(), workflow.getCreatedAt(),
                    workflow.getUpdatedAt());
        }
    }

    public record ApprovalWorkflowDetail(
            Long workflowId,
            String code,
            String name,
            @Schema(nullable = true) String description,
            boolean active,
            List<ApprovalWorkflowStepResponse> steps,
            Instant createdAt,
            Instant updatedAt) {

        static ApprovalWorkflowDetail of(ApprovalWorkflowDefinition workflow) {
            return new ApprovalWorkflowDetail(workflow.getId(), workflow.getCode(), workflow.getName(),
                    workflow.getDescription(), workflow.isActive(),
                    workflow.getSteps().stream().map(ApprovalWorkflowStepResponse::of).toList(), workflow.getCreatedAt(),
                    workflow.getUpdatedAt());
        }
    }

    /** One of the signed-in account's requests in a list. */
    public record ApprovalRequestSummary(
            @Schema(example = "12") Long approvalRequestId,
            @Schema(example = "HOSTEL_APPLICATION") String workflowCode,
            String workflowName,
            @Schema(example = "HOSTEL_APPLICATION") String sourceType,
            @Schema(example = "57") String sourceId,
            @Schema(example = "Hostel application for Semester 4") String subject,
            @Schema(allowableValues = {"PENDING", "APPROVED", "REJECTED", "CANCELLED"}) String status,
            @Schema(nullable = true, example = "1") Integer currentStepOrder,
            Instant submittedAt,
            @Schema(nullable = true) Instant completedAt) {

        static ApprovalRequestSummary of(ApprovalRequest request) {
            ApprovalWorkflowDefinition workflow = request.getWorkflowDefinition();
            return new ApprovalRequestSummary(request.getId(), workflow.getCode(), workflow.getName(),
                    request.getSourceType(), request.getSourceId(), request.getSubject(), request.getStatus().name(),
                    request.getCurrentStepOrder(), request.getSubmittedAt(), request.getCompletedAt());
        }
    }

    public record ApprovalRequestStepResponse(
            int stepOrder,
            String stepName,
            @Schema(example = "FACULTY") String requiredRoleCode,
            @Schema(allowableValues = {"WAITING", "PENDING", "APPROVED", "REJECTED", "SKIPPED"}) String status,
            @Schema(nullable = true, example = "faculty1") String actedByLoginId,
            @Schema(nullable = true) Instant actedAt,
            @Schema(nullable = true, description = "Plain text.") String comment) {

        static ApprovalRequestStepResponse of(ApprovalRequestStep step) {
            return new ApprovalRequestStepResponse(step.getStepOrder(), step.getStepName(), step.getRequiredRoleCode(),
                    step.getStatus().name(), loginIdOf(step.getActedBy()), step.getActedAt(), step.getComment());
        }
    }

    public record ApprovalHistoryResponse(
            @Schema(allowableValues = {"REQUEST_CREATED", "STEP_ACTIVATED", "STEP_APPROVED", "STEP_REJECTED",
                    "REQUEST_APPROVED", "REQUEST_REJECTED", "REQUEST_CANCELLED"}) String action,
            @Schema(nullable = true) Integer stepOrder,
            @Schema(nullable = true, description = "Empty for a transition the engine made itself.") String actorLoginId,
            @Schema(nullable = true) String comment,
            Instant createdAt) {

        static ApprovalHistoryResponse of(ApprovalRequestHistory entry) {
            return new ApprovalHistoryResponse(entry.getAction().name(), entry.getStepOrder(),
                    loginIdOf(entry.getActor()), entry.getComment(), entry.getCreatedAt());
        }
    }

    /** One of the signed-in account's requests in full: its steps and its history, oldest first. */
    public record ApprovalRequestDetail(
            Long approvalRequestId,
            String workflowCode,
            String workflowName,
            String sourceType,
            String sourceId,
            String subject,
            String status,
            @Schema(nullable = true) Integer currentStepOrder,
            Instant submittedAt,
            @Schema(nullable = true) Instant completedAt,
            List<ApprovalRequestStepResponse> steps,
            List<ApprovalHistoryResponse> history) {

        static ApprovalRequestDetail of(ApprovalRequest request, List<ApprovalRequestStep> steps,
                List<ApprovalRequestHistory> history) {
            ApprovalWorkflowDefinition workflow = request.getWorkflowDefinition();
            return new ApprovalRequestDetail(request.getId(), workflow.getCode(), workflow.getName(),
                    request.getSourceType(), request.getSourceId(), request.getSubject(), request.getStatus().name(),
                    request.getCurrentStepOrder(), request.getSubmittedAt(), request.getCompletedAt(),
                    steps.stream().map(ApprovalRequestStepResponse::of).toList(),
                    history.stream().map(ApprovalHistoryResponse::of).toList());
        }
    }

    /** A step waiting for the signed-in approver's decision. */
    public record ApprovalTaskSummary(
            @Schema(example = "31") Long approvalRequestStepId,
            @Schema(example = "12") Long approvalRequestId,
            String workflowCode,
            String workflowName,
            String sourceType,
            String sourceId,
            String subject,
            int stepOrder,
            String stepName,
            @Schema(example = "FACULTY") String requiredRoleCode,
            @Schema(example = "student1") String requesterLoginId,
            Instant submittedAt) {

        static ApprovalTaskSummary of(ApprovalRequestStep step) {
            ApprovalRequest request = step.getApprovalRequest();
            ApprovalWorkflowDefinition workflow = request.getWorkflowDefinition();
            return new ApprovalTaskSummary(step.getId(), request.getId(), workflow.getCode(), workflow.getName(),
                    request.getSourceType(), request.getSourceId(), request.getSubject(), step.getStepOrder(),
                    step.getStepName(), step.getRequiredRoleCode(), loginIdOf(request.getRequester()),
                    request.getSubmittedAt());
        }
    }

    public record ApprovalRequestPage(List<ApprovalRequestSummary> items, int page, int size, long totalElements,
            int totalPages) {
    }

    public record ApprovalTaskPage(List<ApprovalTaskSummary> items, int page, int size, long totalElements,
            int totalPages) {
    }

    /** What a decision did: the step's new status and where the request is now. */
    public record ApprovalDecisionResponse(
            Long approvalRequestStepId,
            Long approvalRequestId,
            @Schema(allowableValues = {"APPROVED", "REJECTED"}) String stepStatus,
            @Schema(allowableValues = {"PENDING", "APPROVED", "REJECTED"}) String requestStatus,
            @Schema(nullable = true, description = "The next step, now waiting for its approvers, while the request is pending.")
            Integer currentStepOrder) {
    }
}
