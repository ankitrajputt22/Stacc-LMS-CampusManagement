package com.stacc.backend.campus.services;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.approval.ApprovalCommands.CurrentApprovalStep;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalResponses.ApprovalHistoryResponse;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestStepResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Campus services as clients see them. No entity is sent as it is. Requesters see services without their workflow
 * code, and their own requests without account details; admins see requesters by login ID only. The request status is
 * the business status; the approval status, when the service needs approval, is the approval engine's and is sent
 * separately.
 */
public final class CampusServiceResponses {

    private CampusServiceResponses() {
    }

    /** A service a requester may ask for. */
    public record CampusServiceSummary(
            @Schema(example = "1") Long serviceId,
            @Schema(example = "ID_CARD") String code,
            @Schema(example = "ID card replacement") String name,
            @Schema(nullable = true) String description,
            boolean approvalRequired) {

        static CampusServiceSummary of(CampusServiceDefinition service) {
            return new CampusServiceSummary(service.getId(), service.getCode(), service.getName(),
                    service.getDescription(), service.isApprovalRequired());
        }
    }

    /** A service with its full configuration, for admins. */
    public record CampusServiceDefinitionResponse(
            Long serviceId,
            @Schema(example = "ID_CARD") String code,
            String name,
            @Schema(nullable = true) String description,
            boolean active,
            boolean availableToStudent,
            boolean availableToFaculty,
            boolean approvalRequired,
            @Schema(nullable = true, example = "CAMPUS_SERVICE_REQUEST") String approvalWorkflowCode,
            Instant createdAt,
            Instant updatedAt) {

        static CampusServiceDefinitionResponse of(CampusServiceDefinition service) {
            return new CampusServiceDefinitionResponse(service.getId(), service.getCode(), service.getName(),
                    service.getDescription(), service.isActive(), service.isAvailableToStudent(),
                    service.isAvailableToFaculty(), service.isApprovalRequired(), service.getApprovalWorkflowCode(),
                    service.getCreatedAt(), service.getUpdatedAt());
        }
    }

    /** One of the requester's own requests in a list. */
    public record CampusRequestSummary(
            Long requestId,
            @Schema(example = "CSR-000001") String requestNumber,
            Long serviceId,
            String serviceCode,
            String serviceName,
            String subject,
            @Schema(allowableValues = {"SUBMITTED", "APPROVED", "REJECTED", "CANCELLED", "COMPLETED"}) String status,
            @Schema(nullable = true, description = "The approval engine's status; empty when the service needs no approval.",
                    allowableValues = {"PENDING", "APPROVED", "REJECTED", "CANCELLED"}) String approvalStatus,
            Instant submittedAt,
            @Schema(nullable = true) Instant completedAt) {

        static CampusRequestSummary of(CampusServiceRequest request) {
            CampusServiceDefinition service = request.getServiceDefinition();
            return new CampusRequestSummary(request.getId(), request.getRequestNumber(), service.getId(),
                    service.getCode(), service.getName(), request.getSubject(), request.effectiveStatus().name(),
                    nameOf(request.approvalStatus()), request.getSubmittedAt(), request.getCompletedAt());
        }
    }

    public record CampusRequestPage(List<CampusRequestSummary> items, int page, int size, long totalElements,
            int totalPages) {
    }

    /** One of the requester's own requests in full, with its approval progress when it has one. */
    public record CampusRequestDetail(
            Long requestId,
            @Schema(example = "CSR-000001") String requestNumber,
            Long serviceId,
            String serviceCode,
            String serviceName,
            String subject,
            @Schema(description = "Plain text.") String details,
            @Schema(allowableValues = {"SUBMITTED", "APPROVED", "REJECTED", "CANCELLED", "COMPLETED"}) String status,
            @Schema(nullable = true, allowableValues = {"PENDING", "APPROVED", "REJECTED", "CANCELLED"}) String approvalStatus,
            @Schema(nullable = true, description = "The comment the approver gave when rejecting, exactly as written.")
            String rejectionComment,
            @Schema(description = "Whether the requester may cancel the request now.") boolean canCancel,
            Instant submittedAt,
            @Schema(nullable = true) Instant completedAt,
            @Schema(description = "The approval steps, in order; empty when the service needs no approval.")
            List<ApprovalRequestStepResponse> approvalSteps) {
    }

    /** One request in the admin queue. */
    public record AdminCampusRequestSummary(
            Long requestId,
            @Schema(example = "CSR-000001") String requestNumber,
            @Schema(example = "student1") String requesterLoginId,
            Long serviceId,
            String serviceCode,
            String serviceName,
            String subject,
            @Schema(allowableValues = {"SUBMITTED", "APPROVED", "REJECTED", "CANCELLED", "COMPLETED"}) String status,
            @Schema(nullable = true, allowableValues = {"PENDING", "APPROVED", "REJECTED", "CANCELLED"}) String approvalStatus,
            Instant submittedAt,
            @Schema(nullable = true) Instant completedAt) {

        static AdminCampusRequestSummary of(CampusServiceRequest request) {
            CampusServiceDefinition service = request.getServiceDefinition();
            return new AdminCampusRequestSummary(request.getId(), request.getRequestNumber(),
                    request.getRequester().getLoginId(), service.getId(), service.getCode(), service.getName(),
                    request.getSubject(), request.effectiveStatus().name(), nameOf(request.approvalStatus()),
                    request.getSubmittedAt(), request.getCompletedAt());
        }
    }

    public record AdminCampusRequestPage(List<AdminCampusRequestSummary> items, int page, int size, long totalElements,
            int totalPages) {
    }

    /**
     * One request for admins: approval progress and history from the approval engine, the step waiting for a decision,
     * whether this admin may decide it through the approval task routes, and whether it can be completed.
     */
    public record AdminCampusRequestDetail(
            Long requestId,
            @Schema(example = "CSR-000001") String requestNumber,
            String requesterLoginId,
            Long serviceId,
            String serviceCode,
            String serviceName,
            String subject,
            String details,
            @Schema(allowableValues = {"SUBMITTED", "APPROVED", "REJECTED", "CANCELLED", "COMPLETED"}) String status,
            @Schema(nullable = true, allowableValues = {"PENDING", "APPROVED", "REJECTED", "CANCELLED"}) String approvalStatus,
            @Schema(nullable = true) String rejectionComment,
            Instant submittedAt,
            @Schema(nullable = true) Instant completedAt,
            List<ApprovalRequestStepResponse> approvalSteps,
            List<ApprovalHistoryResponse> approvalHistory,
            @Schema(nullable = true, description = "The step waiting for a decision. Decide it with POST /api/approvals/tasks/{approvalRequestStepId}/approve or /reject.")
            CurrentApprovalStep currentApprovalStep,
            @Schema(description = "Whether the signed-in admin may decide the current step: they hold its role and APPROVAL_TASK_DECIDE, and are not the requester.")
            boolean canDecide,
            @Schema(description = "Whether the request is approved and can be marked completed.") boolean canComplete) {
    }

    static String nameOf(ApprovalRequestStatus status) {
        return status == null ? null : status.name();
    }
}
