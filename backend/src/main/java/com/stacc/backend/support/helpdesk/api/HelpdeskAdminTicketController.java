package com.stacc.backend.support.helpdesk.api;

import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.AssignHelpdeskTicketRequest;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.ChangeHelpdeskTicketPriorityRequest;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.ChangeHelpdeskTicketStatusRequest;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketDetail;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketPage;
import com.stacc.backend.support.helpdesk.HelpdeskTicketCommandService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketPriority;
import com.stacc.backend.support.helpdesk.HelpdeskTicketQueryService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketStatus;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The support team's ticket queue. Listing needs the admin role and {@code HELPDESK_TICKET_VIEW_ALL}; assigning,
 * prioritising, and moving a ticket need the admin role and {@code HELPDESK_TICKET_MANAGE}. Tickets are never deleted
 * or reopened, and a closed ticket cannot be changed.
 */
@RestController
@RequestMapping("/api/helpdesk/admin/tickets")
@Tag(name = "Helpdesk admin")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HelpdeskAdminTicketController {

    private static final String NOT_FOUND = "The ticket does not exist.";

    private final HelpdeskTicketCommandService commands;
    private final HelpdeskTicketQueryService queries;

    public HelpdeskAdminTicketController(HelpdeskTicketCommandService commands, HelpdeskTicketQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize(HelpdeskAccess.VIEW_ALL)
    @Operation(
            summary = "List the helpdesk queue",
            description = "Returns one page of every ticket, newest first. Each filter that is sent narrows the list; assignedToMe keeps the tickets assigned to the signed-in admin.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of tickets. The list may be empty.")
    @ApiResponse(responseCode = "400",
            description = "A filter is not valid, the page is below 0, or the size is not between 1 and 100.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HelpdeskTicketPage queue(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @Parameter(description = "Only tickets in this status.") @RequestParam(required = false) HelpdeskTicketStatus status,
            @Parameter(description = "Only tickets with this priority.") @RequestParam(required = false) HelpdeskTicketPriority priority,
            @Parameter(description = "Only tickets of this category.") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Only tickets assigned to the signed-in admin.") @RequestParam(defaultValue = "false") boolean assignedToMe,
            @Parameter(description = "Only tickets raised by this login ID.") @RequestParam(required = false) String requesterLoginId,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return queries.queue(HelpdeskTicketController.actor(accessToken, authentication), status, priority, categoryId,
                assignedToMe, requesterLoginId, page, size);
    }

    @PutMapping("/{ticketId}/assignment")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(
            summary = "Assign a helpdesk ticket",
            description = "Assigns the ticket to an admin account by login ID, or unassigns it with null. The same assignee changes nothing. The assigned admin is told in their Inbox, unless they assigned it themselves.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The ticket.")
    @ApiResponse(responseCode = "400", description = "The account does not exist or is not an admin.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The ticket is closed.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HelpdeskTicketDetail assign(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @PathVariable Long ticketId, @Valid @RequestBody AssignHelpdeskTicketRequest request) {
        return commands.assign(HelpdeskTicketController.actor(accessToken, authentication), ticketId,
                request.assignedToLoginId());
    }

    @PutMapping("/{ticketId}/priority")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(
            summary = "Change a ticket's priority",
            description = "Sets LOW, NORMAL, HIGH, or URGENT. The same priority changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The ticket.")
    @ApiResponse(responseCode = "400", description = "The priority is missing or not valid.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The ticket is closed.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HelpdeskTicketDetail priority(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @PathVariable Long ticketId, @Valid @RequestBody ChangeHelpdeskTicketPriorityRequest request) {
        return commands.changePriority(HelpdeskTicketController.actor(accessToken, authentication), ticketId,
                request.priority());
    }

    @PutMapping("/{ticketId}/status")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(
            summary = "Change a ticket's status",
            description = "Moves the ticket forward: OPEN to IN_PROGRESS, RESOLVED, or CLOSED; IN_PROGRESS to RESOLVED or CLOSED; RESOLVED to CLOSED. The same status changes nothing. A real change tells the requester in their Inbox. There is no reopening.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The ticket.")
    @ApiResponse(responseCode = "400", description = "The status is missing or not valid.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The lifecycle does not allow this move, or the ticket is closed.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HelpdeskTicketDetail status(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @PathVariable Long ticketId, @Valid @RequestBody ChangeHelpdeskTicketStatusRequest request) {
        return commands.changeStatus(HelpdeskTicketController.actor(accessToken, authentication), ticketId,
                request.status());
    }
}
