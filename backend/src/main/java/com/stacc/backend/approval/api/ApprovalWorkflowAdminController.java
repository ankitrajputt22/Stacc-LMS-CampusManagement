package com.stacc.backend.approval.api;

import java.util.List;

import com.stacc.backend.approval.ApprovalApiRequests.CreateApprovalWorkflowRequest;
import com.stacc.backend.approval.ApprovalApiRequests.UpdateApprovalWorkflowRequest;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalResponses.ApprovalWorkflowDetail;
import com.stacc.backend.approval.ApprovalResponses.ApprovalWorkflowSummary;
import com.stacc.backend.approval.ApprovalWorkflowDefinitionService;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin configuration of approval workflows. Every route needs the admin role and {@code APPROVAL_WORKFLOW_MANAGE}. A
 * workflow's code is fixed once created; workflows are made inactive instead of deleted, and editing one changes
 * future requests only.
 */
@RestController
@RequestMapping("/api/approvals/admin/workflows")
@Tag(name = "Approval workflows admin")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class ApprovalWorkflowAdminController {

    private final ApprovalQueryService queries;
    private final ApprovalWorkflowDefinitionService definitions;

    public ApprovalWorkflowAdminController(ApprovalQueryService queries, ApprovalWorkflowDefinitionService definitions) {
        this.queries = queries;
        this.definitions = definitions;
    }

    @GetMapping
    @PreAuthorize(ApprovalAccess.WORKFLOW_MANAGE)
    @Operation(summary = "List approval workflows", description = "Returns every workflow, active and inactive, by code.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The workflows. The list may be empty.")
    public List<ApprovalWorkflowSummary> list() {
        return queries.workflows();
    }

    @GetMapping("/{workflowId}")
    @PreAuthorize(ApprovalAccess.WORKFLOW_MANAGE)
    @Operation(summary = "Get one approval workflow", description = "Returns the workflow with its ordered steps.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The workflow.")
    @ApiResponse(responseCode = "404", description = "The workflow does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApprovalWorkflowDetail get(@PathVariable Long workflowId) {
        return queries.workflow(workflowId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ApprovalAccess.WORKFLOW_MANAGE)
    @Operation(
            summary = "Create an approval workflow",
            description = "Adds an active workflow with 1 to 10 steps. Each step names an existing role that can decide approval steps; the steps are numbered from 1 in the order sent. The code is stored in uppercase and never changes.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new workflow.")
    @ApiResponse(responseCode = "400", description = "A field is missing or too long, the code has other characters, there are no or too many steps, or a role is unknown or cannot decide steps.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "A workflow with this code already exists.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApprovalWorkflowDetail create(@Valid @RequestBody CreateApprovalWorkflowRequest request) {
        return definitions.create(request);
    }

    @PutMapping("/{workflowId}")
    @PreAuthorize(ApprovalAccess.WORKFLOW_MANAGE)
    @Operation(
            summary = "Update an approval workflow",
            description = "Replaces the name, description, active flag, and steps. The code is not part of the request and never changes. The new steps apply to requests started from now on; running and finished requests keep the steps they started with. An inactive workflow starts no new requests.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The updated workflow.")
    @ApiResponse(responseCode = "400", description = "A field is missing or too long, there are no or too many steps, or a role is unknown or cannot decide steps.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The workflow does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The workflow was changed at the same time.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApprovalWorkflowDetail update(@PathVariable Long workflowId,
            @Valid @RequestBody UpdateApprovalWorkflowRequest request) {
        return definitions.update(workflowId, request);
    }
}
