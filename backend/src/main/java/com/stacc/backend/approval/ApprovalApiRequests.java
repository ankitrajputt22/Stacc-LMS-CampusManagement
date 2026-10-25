package com.stacc.backend.approval;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What clients may send to the approval API. No request names a requester, actor, step number, status, or time: those
 * come from the server and the access token. There is no request to start an approval.
 */
public final class ApprovalApiRequests {

    private ApprovalApiRequests() {
    }

    public record ApprovalWorkflowStepRequest(
            @Schema(example = "Faculty review")
            @NotBlank(message = "Step name is required")
            @Size(max = ApprovalWorkflowDefinition.MAX_NAME_LENGTH, message = "Step name must be at most 150 characters")
            String name,

            @Schema(description = "The code of an existing role whose holders decide this step.", example = "FACULTY")
            @NotBlank(message = "Approver role is required")
            @Size(max = 50, message = "Approver role must be at most 50 characters")
            String approverRoleCode) {
    }

    public record CreateApprovalWorkflowRequest(
            @Schema(description = "Stored in uppercase: letters, digits, and underscores. Unique, and never changed.",
                    example = "HOSTEL_APPLICATION")
            @NotBlank(message = "Code is required")
            @Size(max = ApprovalWorkflowDefinition.MAX_CODE_LENGTH, message = "Code must be at most 80 characters")
            String code,

            @Schema(example = "Hostel application")
            @NotBlank(message = "Name is required")
            @Size(max = ApprovalWorkflowDefinition.MAX_NAME_LENGTH, message = "Name must be at most 150 characters")
            String name,

            @Schema(description = "Optional plain text.", nullable = true)
            @Size(max = ApprovalWorkflowDefinition.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 2000 characters")
            String description,

            @Schema(description = "The steps in the order they are decided, 1 to 10. Their numbers come from this order.")
            @NotNull(message = "Steps are required")
            @Size(min = 1, max = ApprovalWorkflowDefinition.MAX_STEPS, message = "A workflow has 1 to 10 steps")
            List<@NotNull(message = "A step is required") @Valid ApprovalWorkflowStepRequest> steps) {
    }

    public record UpdateApprovalWorkflowRequest(
            @Schema(example = "Hostel application")
            @NotBlank(message = "Name is required")
            @Size(max = ApprovalWorkflowDefinition.MAX_NAME_LENGTH, message = "Name must be at most 150 characters")
            String name,

            @Schema(nullable = true)
            @Size(max = ApprovalWorkflowDefinition.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 2000 characters")
            String description,

            @Schema(description = "Whether the workflow can start new requests. Existing requests are not affected.",
                    example = "true")
            @NotNull(message = "Active is required")
            Boolean active,

            @Schema(description = "The new steps, in order. They apply to requests started from now on only.")
            @NotNull(message = "Steps are required")
            @Size(min = 1, max = ApprovalWorkflowDefinition.MAX_STEPS, message = "A workflow has 1 to 10 steps")
            List<@NotNull(message = "A step is required") @Valid ApprovalWorkflowStepRequest> steps) {
    }

    public record ApproveStepRequest(
            @Schema(description = "Optional plain text.", nullable = true, example = "Room allocation confirmed.")
            @Size(max = ApprovalRequestStep.MAX_COMMENT_LENGTH, message = "Comment must be at most 5000 characters")
            String comment) {
    }

    public record RejectStepRequest(
            @Schema(description = "Required plain text: why the request is rejected.", example = "Fees are not yet paid.")
            @NotBlank(message = "A comment is required to reject")
            @Size(max = ApprovalRequestStep.MAX_COMMENT_LENGTH, message = "Comment must be at most 5000 characters")
            String comment) {
    }
}
