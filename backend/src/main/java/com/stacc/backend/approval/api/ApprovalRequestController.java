package com.stacc.backend.approval.api;

import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestDetail;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestPage;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in account's own approval requests. Requests are started by business modules on their users' behalf, so
 * there is no route here to start or cancel one, and no account or requester ID is ever accepted.
 */
@RestController
@RequestMapping("/api/approvals")
@Tag(name = "Approvals")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class ApprovalRequestController {

    private final ApprovalQueryService queries;

    public ApprovalRequestController(ApprovalQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/me")
    @PreAuthorize(ApprovalAccess.VIEW_OWN)
    @Operation(
            summary = "List my approval requests",
            description = "Returns one page of the signed-in account's own approval requests, newest first, optionally only those in one status.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of requests. The list may be empty.")
    @ApiResponse(responseCode = "400", description = "The status is not valid, the page is below 0, or the size is not between 1 and 100.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApprovalRequestPage mine(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @Parameter(description = "Only requests in this status.") @RequestParam(required = false) ApprovalRequestStatus status,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return queries.ownRequests(ApprovalAccess.actor(accessToken, authentication), status, page, size);
    }

    @GetMapping("/requests/{approvalRequestId}")
    @PreAuthorize(ApprovalAccess.VIEW_OWN)
    @Operation(
            summary = "Get one of my approval requests",
            description = "Returns one of the signed-in account's own approval requests with its steps and history, oldest first. Approvers use the task routes instead.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The request.")
    @ApiResponse(responseCode = "404", description = "The request does not exist, or belongs to another account.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApprovalRequestDetail request(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @PathVariable Long approvalRequestId) {
        return queries.ownRequest(ApprovalAccess.actor(accessToken, authentication), approvalRequestId);
    }
}
