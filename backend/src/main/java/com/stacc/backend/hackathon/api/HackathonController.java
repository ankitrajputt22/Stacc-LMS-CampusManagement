package com.stacc.backend.hackathon.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.hackathon.HackathonService;
import com.stacc.backend.hackathon.HackathonService.HackathonView;
import com.stacc.backend.hackathon.HackathonService.JudgeView;
import com.stacc.backend.hackathon.HackathonService.OfficeView;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Hackathons: teams and submissions for students, scoring for judges, and running them for the campus office. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Hackathons")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "No such hackathon, team, or submission for the signed-in account.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HackathonController {

    private static final String PARTICIPATE = "hasRole('STUDENT') and hasAuthority('HACKATHON_PARTICIPATE')";
    private static final String JUDGE = "hasRole('FACULTY') and hasAuthority('HACKATHON_JUDGE')";
    private static final String MANAGE = "hasRole('ADMIN') and hasAuthority('HACKATHON_MANAGE')";

    private final HackathonService service;

    public HackathonController(HackathonService service) {
        this.service = service;
    }

    public record TeamRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record SubmissionRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 5000) String summary, @Size(max = 500) String link) {
    }

    public record ScoreRequest(@Min(0) @Max(100) int score, @Size(max = 1000) String comment) {
    }

    public record HackathonRequest(@NotBlank @Size(max = 200) String title, @Size(max = 5000) String description, @NotNull Instant startsAt,
            @NotNull Instant endsAt, @Min(1) @Max(10) int maxTeamSize) {
    }

    public record JudgeRequest(@NotBlank @Size(max = 100) String loginId) {
    }

    @GetMapping("/hackathons")
    @PreAuthorize(PARTICIPATE)
    @Operation(summary = "Hackathons", description = "Published ones with their teams; my team's submission; results only once released.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<HackathonView> visible(@AuthenticationPrincipal Jwt token) {
        return service.visible(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/hackathons/{hackathonId}/teams")
    @PreAuthorize(PARTICIPATE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Form a team", description = "Before the end; one team per hackathon each.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void createTeam(@PathVariable Long hackathonId, @Valid @RequestBody TeamRequest request, @AuthenticationPrincipal Jwt token) {
        service.createTeam(AuthenticatedAccount.idFrom(token), hackathonId, request.name());
    }

    @PostMapping("/hackathons/teams/{teamId}/join")
    @PreAuthorize(PARTICIPATE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Join a team", description = "A team with a free place, before the end.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "The team is full, the hackathon has ended, or already on a team.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public void joinTeam(@PathVariable Long teamId, @AuthenticationPrincipal Jwt token) {
        service.joinTeam(AuthenticatedAccount.idFrom(token), teamId);
    }

    @PostMapping("/hackathons/{hackathonId}/leave")
    @PreAuthorize(PARTICIPATE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Leave my team", description = "Before the end.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void leave(@PathVariable Long hackathonId, @AuthenticationPrincipal Jwt token) {
        service.leaveTeam(AuthenticatedAccount.idFrom(token), hackathonId);
    }

    @PostMapping("/hackathons/{hackathonId}/submission")
    @PreAuthorize(PARTICIPATE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Hand in my team's work", description = "One submission per team, replaced until the end.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void submit(@PathVariable Long hackathonId, @Valid @RequestBody SubmissionRequest request, @AuthenticationPrincipal Jwt token) {
        service.submit(AuthenticatedAccount.idFrom(token), hackathonId, request.title(), request.summary(), request.link());
    }

    @GetMapping("/hackathons/judging")
    @PreAuthorize(JUDGE)
    @Operation(summary = "Hackathons I judge", description = "Submissions after the end, each with my own score only.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<JudgeView> judging(@AuthenticationPrincipal Jwt token) {
        return service.judging(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/hackathons/submissions/{submissionId}/score")
    @PreAuthorize(JUDGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Score a submission", description = "0 to 100, replaced until results are released.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void score(@PathVariable Long submissionId, @Valid @RequestBody ScoreRequest request, @AuthenticationPrincipal Jwt token) {
        service.score(AuthenticatedAccount.idFrom(token), submissionId, request.score(), request.comment());
    }

    @GetMapping("/admin/hackathons")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Every hackathon with judges and standings", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<OfficeView> all() {
        return service.all();
    }

    @PostMapping("/admin/hackathons")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Add a hackathon", description = "It starts as a draft.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void create(@Valid @RequestBody HackathonRequest request) {
        service.create(request.title(), request.description(), request.startsAt(), request.endsAt(), request.maxTeamSize());
    }

    @PostMapping("/admin/hackathons/{hackathonId}/publish")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Publish a hackathon", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void publish(@PathVariable Long hackathonId) {
        service.publish(hackathonId);
    }

    @PostMapping("/admin/hackathons/{hackathonId}/judges")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Name a judge", description = "An active faculty account.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void addJudge(@PathVariable Long hackathonId, @Valid @RequestBody JudgeRequest request) {
        service.addJudge(hackathonId, request.loginId());
    }

    @PostMapping("/admin/hackathons/{hackathonId}/release")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Release the results", description = "Once, after the end; participants then see totals and places.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void release(@PathVariable Long hackathonId) {
        service.releaseResults(hackathonId);
    }

    @PostMapping("/admin/hackathons/{hackathonId}/archive")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive a hackathon", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void archive(@PathVariable Long hackathonId) {
        service.archive(hackathonId);
    }
}
