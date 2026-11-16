package com.stacc.backend.engagement.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.engagement.tournaments.CampusFixture;
import com.stacc.backend.engagement.tournaments.TournamentService;
import com.stacc.backend.engagement.tournaments.TournamentService.FixtureInput;
import com.stacc.backend.engagement.tournaments.TournamentService.TeamOption;
import com.stacc.backend.engagement.tournaments.TournamentService.TournamentView;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tournaments: published ones with fixtures and standings, and the campus office's management of them. */
@RestController
@RequestMapping("/api/v1/engagement")
@Tag(name = "Campus engagement")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class TournamentController {

    private static final String USE = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('CAMPUS_ENGAGEMENT_USE')";
    private static final String MANAGE = "hasRole('ADMIN') and hasAuthority('CAMPUS_ENGAGEMENT_MANAGE')";

    private final TournamentService service;

    public TournamentController(TournamentService service) {
        this.service = service;
    }

    public record TournamentRequest(@NotBlank @Size(max = 150) String name, @NotBlank @Size(max = 100) String sport,
            @NotBlank @Size(max = 30) String season) {
    }

    public record FixtureRequest(Long homeTeamId, @Size(max = 150) String homeName, Long awayTeamId, @Size(max = 150) String awayName,
            @NotNull Instant startsAt, @Size(max = 200) String venue) {
    }

    public record ResultRequest(@NotBlank @Size(max = 50) String homeScore, @NotBlank @Size(max = 50) String awayScore,
            @NotNull CampusFixture.Result result) {
    }

    @GetMapping("/tournaments")
    @PreAuthorize(USE)
    @Operation(summary = "Published tournaments", description = "With fixtures, text scores, and standings worked out from played matches.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<TournamentView> published() {
        return service.published();
    }

    @GetMapping("/admin/tournaments")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Every tournament", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<TournamentView> all() {
        return service.all();
    }

    @GetMapping("/admin/tournaments/teams")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Active teams to choose sides from", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<TeamOption> teams() {
        return service.teamOptions();
    }

    @PostMapping("/admin/tournaments")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a tournament", description = "It starts as a draft.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public TournamentView create(@Valid @RequestBody TournamentRequest request, @AuthenticationPrincipal Jwt token) {
        return service.create(AuthenticatedAccount.idFrom(token), request.name(), request.sport(), request.season());
    }

    @PostMapping("/admin/tournaments/{tournamentId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Publish a tournament", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public TournamentView publish(@PathVariable Long tournamentId) {
        return service.publish(tournamentId);
    }

    @PostMapping("/admin/tournaments/{tournamentId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Archive a tournament", description = "It can no longer be changed.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public TournamentView archive(@PathVariable Long tournamentId) {
        return service.archive(tournamentId);
    }

    @PostMapping("/admin/tournaments/{tournamentId}/fixtures")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a match", description = "Each side is one of our active teams or an external opponent's name.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "An archived tournament or an inactive team.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public TournamentView addFixture(@PathVariable Long tournamentId, @Valid @RequestBody FixtureRequest request) {
        return service.addFixture(tournamentId, new FixtureInput(request.homeTeamId(), request.homeName(), request.awayTeamId(),
                request.awayName(), request.startsAt(), request.venue()));
    }

    @PostMapping("/admin/tournaments/fixtures/{fixtureId}/result")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Record a match result", description = "Text scores and the result; it may be corrected.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "A cancelled match or an archived tournament.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public TournamentView record(@PathVariable Long fixtureId, @Valid @RequestBody ResultRequest request, @AuthenticationPrincipal Jwt token) {
        return service.record(AuthenticatedAccount.idFrom(token), fixtureId, request.homeScore(), request.awayScore(), request.result());
    }

    @PostMapping("/admin/tournaments/fixtures/{fixtureId}/cancel")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Cancel a match", description = "It stays listed; a played match is corrected instead.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public TournamentView cancel(@PathVariable Long fixtureId) {
        return service.cancel(fixtureId);
    }
}
