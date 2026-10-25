package com.stacc.backend.approval.api;

import com.stacc.backend.approval.ApprovalApiRequests.ApproveStepRequest;
import com.stacc.backend.approval.ApprovalApiRequests.RejectStepRequest;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalResponses.ApprovalDecisionResponse;
import com.stacc.backend.approval.ApprovalResponses.ApprovalTaskPage;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The approver's queue and decisions. A step is shown and decided only for an account holding the exact role the step
 * requires, and never for the requester's own request; ADMIN gets no pass. The deciding account always comes from the
 * access token.
 */
@RestController
@RequestMapping("/api/approvals/tasks")
@Tag(name = "Approval tasks")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class ApprovalTaskController {

    private static final String DECISION_404 = "The step does not exist.";
    private static final String DECISION_409 =
            "The request is no longer pending, the step was already decided, or it is not the current step yet.";

    private final ApprovalQueryService queries;
    private final ApprovalWorkflowService workflow;

    public ApprovalTaskController(ApprovalQueryService queries, ApprovalWorkflowService workflow) {
        this.queries = queries;
        this.workflow = workflow;
    }

    @GetMapping
    @PreAuthorize(ApprovalAccess.TASK_VIEW)
    @Operation(
            summary = "List my approval tasks",
            description = "Returns one page of the steps waiting for the signed-in account's decision: current steps of pending requests that require one of its roles, oldest first. Its own requests are never listed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of tasks. The list may be empty.")
    @ApiResponse(responseCode = "400", description = "The page is below 0, or the size is not between 1 and 100.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApprovalTaskPage tasks(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return queries.tasks(ApprovalAccess.actor(accessToken, authentication), page, size);
    }

    @PostMapping("/{approvalRequestStepId}/approve")
    @PreAuthorize(ApprovalAccess.TASK_DECIDE)
    @Operation(
            summary = "Approve a step",
            description = "Approves the current step of a pending request, with an optional plain-text comment. The next step then waits for its approvers, or, after the last step, the request is approved and the requester is told. The account must hold the role the step requires.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The step is approved.")
    @ApiResponse(responseCode = "400", description = "The comment is too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = DECISION_404,
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = DECISION_409,
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApprovalDecisionResponse approve(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @PathVariable Long approvalRequestStepId, @Valid @RequestBody(required = false) ApproveStepRequest request) {
        return workflow.approveStep(ApprovalAccess.actor(accessToken, authentication), approvalRequestStepId,
                request == null ? null : request.comment());
    }

    @PostMapping("/{approvalRequestStepId}/reject")
    @PreAuthorize(ApprovalAccess.TASK_DECIDE)
    @Operation(
            summary = "Reject a step",
            description = "Rejects the current step of a pending request with a required plain-text reason. The request is rejected, later steps are skipped, and the requester is told. The account must hold the role the step requires.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The step and the request are rejected.")
    @ApiResponse(responseCode = "400", description = "The comment is missing, blank, or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = DECISION_404,
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = DECISION_409,
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApprovalDecisionResponse reject(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @PathVariable Long approvalRequestStepId, @Valid @RequestBody RejectStepRequest request) {
        return workflow.rejectStep(ApprovalAccess.actor(accessToken, authentication), approvalRequestStepId,
                request.comment());
    }
}
