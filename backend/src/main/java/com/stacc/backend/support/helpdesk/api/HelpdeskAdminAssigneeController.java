package com.stacc.backend.support.helpdesk.api;

import java.util.List;

import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskAssigneeResponse;
import com.stacc.backend.support.helpdesk.HelpdeskTicketQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who tickets can be assigned to, for the support team's assignment choice. It needs the admin role and
 * {@code HELPDESK_TICKET_MANAGE}, and lists login IDs only.
 */
@RestController
@RequestMapping("/api/helpdesk/admin/assignees")
@Tag(name = "Helpdesk admin")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HelpdeskAdminAssigneeController {

    private final HelpdeskTicketQueryService queries;

    public HelpdeskAdminAssigneeController(HelpdeskTicketQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(
            summary = "List helpdesk assignees",
            description = "Returns the active admin accounts a ticket can be assigned to, by login ID, in login ID order. Assignment checks the same rule again.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The login IDs. The list may be empty.")
    public List<HelpdeskAssigneeResponse> assignees(@AuthenticationPrincipal Jwt accessToken,
            Authentication authentication) {
        return queries.assignees(HelpdeskTicketController.actor(accessToken, authentication));
    }
}
