package com.stacc.backend.engagement.api;

import java.util.Set;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.engagement.teams.CampusTeamMember;
import com.stacc.backend.engagement.teams.TeamService;
import com.stacc.backend.engagement.teams.TeamService.GroupTeams;
import com.stacc.backend.engagement.teams.TeamService.TeamView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Teams of sports groups: rosters by season, with a captain and a vice-captain, managed by officers and the campus office. */
@RestController
@RequestMapping("/api/v1/engagement")
@Tag(name = "Campus engagement")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "No such group, team, or player that the account may see.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class TeamController {

    private static final String USE = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('CAMPUS_ENGAGEMENT_USE')";
    private static final String MANAGE = "hasRole('ADMIN') and hasAuthority('CAMPUS_ENGAGEMENT_MANAGE')";
    private static final String ANY = "(" + USE + ") or (" + MANAGE + ")";

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    public record TeamRequest(@NotBlank @Size(max = 150) String name, @NotBlank @Size(max = 30) String season) {
    }

    public record PlayerRequest(@NotBlank @Size(max = 100) String loginId, @NotNull CampusTeamMember.Role role) {
    }

    public record RoleRequest(@NotNull CampusTeamMember.Role role) {
    }

    private static boolean office(Authentication authentication) {
        Set<String> held = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        return held.contains("ROLE_ADMIN") && held.contains("CAMPUS_ENGAGEMENT_MANAGE");
    }

    @GetMapping("/groups/{groupId}/teams")
    @PreAuthorize(ANY)
    @Operation(summary = "A group's teams", description = "Each with its current roster, for active members, officers, and the campus office.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public GroupTeams teams(@PathVariable Long groupId, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        return service.forGroup(AuthenticatedAccount.idFrom(token), office(authentication), groupId);
    }

    @PostMapping("/groups/{groupId}/teams")
    @PreAuthorize(ANY)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a team", description = "For a sports group's officers and the campus office; one team per name and season.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Not a sports group, an inactive group, or the name is taken for that season.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public TeamView create(@PathVariable Long groupId, @Valid @RequestBody TeamRequest request, @AuthenticationPrincipal Jwt token,
            Authentication authentication) {
        return service.create(AuthenticatedAccount.idFrom(token), office(authentication), groupId, request.name(), request.season());
    }

    @PostMapping("/teams/{teamId}/deactivate")
    @PreAuthorize(ANY)
    @Operation(summary = "Make a team inactive", description = "Its roster is kept.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public TeamView deactivate(@PathVariable Long teamId, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        return service.deactivate(AuthenticatedAccount.idFrom(token), office(authentication), teamId);
    }

    @PostMapping("/teams/{teamId}/players")
    @PreAuthorize(ANY)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a player", description = "An active member of the group, by college ID; a new captain or vice-captain takes over that role.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Not an active member, already on the team, or an inactive team.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public TeamView addPlayer(@PathVariable Long teamId, @Valid @RequestBody PlayerRequest request, @AuthenticationPrincipal Jwt token,
            Authentication authentication) {
        return service.addPlayer(AuthenticatedAccount.idFrom(token), office(authentication), teamId, request.loginId(), request.role());
    }

    @PostMapping("/teams/players/{playerId}/role")
    @PreAuthorize(ANY)
    @Operation(summary = "Change a player's role", description = "A new captain or vice-captain makes the previous one a player.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public TeamView changeRole(@PathVariable Long playerId, @Valid @RequestBody RoleRequest request, @AuthenticationPrincipal Jwt token,
            Authentication authentication) {
        return service.changeRole(AuthenticatedAccount.idFrom(token), office(authentication), playerId, request.role());
    }

    @PostMapping("/teams/players/{playerId}/remove")
    @PreAuthorize(ANY)
    @Operation(summary = "Take a player off the roster", description = "The record is kept as history.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public TeamView removePlayer(@PathVariable Long playerId, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        return service.removePlayer(AuthenticatedAccount.idFrom(token), office(authentication), playerId);
    }
}
