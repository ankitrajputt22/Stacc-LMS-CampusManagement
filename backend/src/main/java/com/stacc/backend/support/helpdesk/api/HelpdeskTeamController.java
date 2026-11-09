package com.stacc.backend.support.helpdesk.api;

import java.util.List;

import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketDetail;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketPage;
import com.stacc.backend.support.helpdesk.HelpdeskTeamService;
import com.stacc.backend.support.helpdesk.HelpdeskTeamService.TeamView;
import com.stacc.backend.support.helpdesk.HelpdeskTicketCommandService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketQueryService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Support teams and routing (admins), the team queue (team members), notes, reopening, and ratings. */
@RestController
@Tag(name = "Helpdesk teams")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HelpdeskTeamController {

    public record TeamRequest(@NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 150) String name) {
    }

    public record TeamUpdateRequest(@NotBlank @Size(max = 150) String name, boolean active) {
    }

    public record MembersRequest(@NotNull @Size(max = 50) List<@NotBlank @Size(max = 100) String> loginIds) {
    }

    public record RouteRequest(Long teamId) {
    }

    public record NoteRequest(@NotBlank @Size(max = 10_000) String message) {
    }

    public record RatingRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {
    }

    private final HelpdeskTeamService teams;
    private final HelpdeskTicketCommandService commands;
    private final HelpdeskTicketQueryService queries;

    public HelpdeskTeamController(HelpdeskTeamService teams, HelpdeskTicketCommandService commands, HelpdeskTicketQueryService queries) {
        this.teams = teams;
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping("/api/v1/helpdesk/admin/teams")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(summary = "List support teams with their members", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<TeamView> list() {
        return teams.all();
    }

    @PostMapping("/api/v1/helpdesk/admin/teams")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a support team", description = "The code is uppercase and fixed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public TeamView create(@Valid @RequestBody TeamRequest request) {
        return teams.create(request.code(), request.name());
    }

    @PutMapping("/api/v1/helpdesk/admin/teams/{teamId}")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(summary = "Rename a team or make it inactive", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public TeamView update(@PathVariable Long teamId, @Valid @RequestBody TeamUpdateRequest request) {
        return teams.update(teamId, request.name(), request.active());
    }

    @PutMapping("/api/v1/helpdesk/admin/teams/{teamId}/members")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(summary = "Set a team's members", description = "Active accounts holding ADMIN or a role with HELPDESK_TEAM_WORK.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public TeamView members(@PathVariable Long teamId, @Valid @RequestBody MembersRequest request) {
        return teams.setMembers(teamId, request.loginIds());
    }

    @PutMapping("/api/v1/helpdesk/admin/categories/{categoryId}/team")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Route a category's new tickets to a team", description = "A null team routes to none.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void route(@PathVariable Long categoryId, @RequestBody RouteRequest request) {
        teams.routeCategory(categoryId, request.teamId());
    }

    @PutMapping("/api/v1/helpdesk/admin/tickets/{ticketId}/team")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(summary = "Move a ticket to another team", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public HelpdeskTicketDetail changeTeam(@AuthenticationPrincipal Jwt token, Authentication authentication,
            @PathVariable Long ticketId, @RequestBody RouteRequest request) {
        return commands.changeTeam(HelpdeskTicketController.actor(token, authentication), ticketId,
                request.teamId() == null ? null : teams.team(request.teamId()));
    }

    @GetMapping("/api/v1/helpdesk/team/tickets")
    @PreAuthorize(HelpdeskAccess.TEAM)
    @Operation(summary = "List your teams' tickets", description = "Tickets routed to the active teams you belong to, newest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public HelpdeskTicketPage teamQueue(@AuthenticationPrincipal Jwt token, Authentication authentication,
            @RequestParam(required = false) HelpdeskTicketStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return queries.teamQueue(HelpdeskTicketController.actor(token, authentication), status, page, size);
    }

    @PostMapping("/api/v1/helpdesk/tickets/{ticketId}/notes")
    @PreAuthorize(HelpdeskAccess.NOTE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an internal note", description = "Seen only by admins with every ticket and the ticket's team; never the requester.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void note(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long ticketId,
            @Valid @RequestBody NoteRequest request) {
        commands.addMessage(HelpdeskTicketController.actor(token, authentication), ticketId, request.message(), true);
    }

    @PostMapping("/api/v1/helpdesk/tickets/{ticketId}/reopen")
    @PreAuthorize(HelpdeskAccess.REPLY)
    @Operation(summary = "Reopen your resolved ticket", description = "Within 7 days of its resolution; a closed ticket never reopens.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public HelpdeskTicketDetail reopen(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long ticketId) {
        return commands.reopen(HelpdeskTicketController.actor(token, authentication), ticketId);
    }

    @PostMapping("/api/v1/helpdesk/tickets/{ticketId}/rating")
    @PreAuthorize(HelpdeskAccess.REPLY)
    @Operation(summary = "Rate your resolved or closed ticket", description = "1 to 5, once, with an optional plain-text comment.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public HelpdeskTicketDetail rate(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long ticketId,
            @Valid @RequestBody RatingRequest request) {
        return commands.rate(HelpdeskTicketController.actor(token, authentication), ticketId, request.rating(), request.comment());
    }
}
