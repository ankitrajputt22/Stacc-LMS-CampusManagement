package com.stacc.backend.support.helpdesk.api;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.stacc.backend.support.helpdesk.HelpdeskActor;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.AddHelpdeskMessageRequest;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.CreateHelpdeskTicketRequest;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskMessageResponse;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketDetail;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketPage;
import com.stacc.backend.support.helpdesk.HelpdeskTicketCommandService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketPriority;
import com.stacc.backend.support.helpdesk.HelpdeskTicketQueryService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Raising, reading, and answering tickets. The requester and every author always come from the access token; no route
 * takes an account, requester, or author ID. A ticket the caller may not see answers the same 404 as a missing one.
 */
@RestController
@RequestMapping("/api/helpdesk/tickets")
@Tag(name = "Helpdesk")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HelpdeskTicketController {

    private final HelpdeskTicketCommandService commands;
    private final HelpdeskTicketQueryService queries;

    public HelpdeskTicketController(HelpdeskTicketCommandService commands, HelpdeskTicketQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(HelpdeskAccess.CREATE)
    @Operation(
            summary = "Raise a helpdesk ticket",
            description = "Creates a ticket for the signed-in account in an active category. It starts OPEN and unassigned, with a ticket number such as STC-000042. Requesters may choose LOW, NORMAL, or HIGH priority; URGENT is set only by the support team.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new ticket with its history.")
    @ApiResponse(responseCode = "400",
            description = "A field is missing or too long, the category is inactive, or the priority is URGENT.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The category does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HelpdeskTicketDetail create(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @Valid @RequestBody CreateHelpdeskTicketRequest request) {
        return commands.create(actor(accessToken, authentication), request);
    }

    @GetMapping("/me")
    @PreAuthorize(HelpdeskAccess.VIEW_OWN)
    @Operation(
            summary = "List my helpdesk tickets",
            description = "Returns one page of the signed-in account's own tickets, newest first. Each filter that is sent narrows the list.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of tickets. The list may be empty.")
    @ApiResponse(responseCode = "400",
            description = "A filter is not valid, the page is below 0, or the size is not between 1 and 100.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HelpdeskTicketPage mine(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @Parameter(description = "Only tickets in this status.") @RequestParam(required = false) HelpdeskTicketStatus status,
            @Parameter(description = "Only tickets of this category.") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Only tickets with this priority.") @RequestParam(required = false) HelpdeskTicketPriority priority,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return queries.ownTickets(actor(accessToken, authentication), status, priority, categoryId, page, size);
    }

    @GetMapping("/{ticketId}")
    @PreAuthorize(HelpdeskAccess.VIEW_ONE)
    @Operation(
            summary = "Get one helpdesk ticket",
            description = "Returns the ticket with its whole conversation and history, oldest first. Requesters see their own tickets; admins with HELPDESK_TICKET_VIEW_ALL see every ticket.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The ticket.")
    @ApiResponse(responseCode = "404", description = "The ticket does not exist, or the caller may not see it.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HelpdeskTicketDetail ticket(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @PathVariable Long ticketId) {
        return queries.ticket(actor(accessToken, authentication), ticketId);
    }

    @PostMapping("/{ticketId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(HelpdeskAccess.REPLY)
    @Operation(
            summary = "Reply to a helpdesk ticket",
            description = "Adds a plain-text message as the signed-in account. Messages are never edited or deleted. Replying is possible while the ticket is OPEN, IN_PROGRESS, or RESOLVED, and never changes its status. A reply from someone else tells the requester in their Inbox.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new message.")
    @ApiResponse(responseCode = "400", description = "The message is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The ticket does not exist, or the caller may not see it.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The ticket is closed.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HelpdeskMessageResponse reply(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @PathVariable Long ticketId, @Valid @RequestBody AddHelpdeskMessageRequest request) {
        return commands.addMessage(actor(accessToken, authentication), ticketId, request.message());
    }

    static HelpdeskActor actor(Jwt accessToken, Authentication authentication) {
        return HelpdeskActor.of(AuthenticatedAccount.idFrom(accessToken), authentication.getAuthorities());
    }
}
