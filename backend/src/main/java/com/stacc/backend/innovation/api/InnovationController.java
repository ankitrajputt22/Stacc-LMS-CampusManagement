package com.stacc.backend.innovation.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.innovation.InnovationIdea;
import com.stacc.backend.innovation.InnovationService;
import com.stacc.backend.innovation.InnovationService.IdeaView;
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

/** Innovation ideas: submitting, reviewing, and the campus office's decisions. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Innovation")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "No such idea that the account may act on.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class InnovationController {

    private static final String SUBMIT = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('INNOVATION_SUBMIT')";
    private static final String REVIEW = "hasRole('FACULTY') and hasAuthority('INNOVATION_REVIEW')";
    private static final String MANAGE = "hasRole('ADMIN') and hasAuthority('INNOVATION_MANAGE')";

    private final InnovationService service;

    public InnovationController(InnovationService service) {
        this.service = service;
    }

    public record IdeaRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = InnovationIdea.MAX_TEXT) String problem,
            @NotBlank @Size(max = InnovationIdea.MAX_TEXT) String solution) {
    }

    public record ReviewRequest(@Min(1) @Max(5) int score, @NotBlank @Size(max = InnovationIdea.MAX_TEXT) String comment) {
    }

    public record DecisionRequest(@NotNull InnovationIdea.Status status, @Size(max = 1000) String note) {
    }

    @GetMapping("/innovation/ideas/me")
    @PreAuthorize(SUBMIT)
    @Operation(summary = "My ideas", description = "With the decision and, once decided, the reviewers' comments without names.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<IdeaView> mine(@AuthenticationPrincipal Jwt token) {
        return service.mine(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/innovation/ideas")
    @PreAuthorize(SUBMIT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit an idea", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public IdeaView submit(@Valid @RequestBody IdeaRequest request, @AuthenticationPrincipal Jwt token) {
        return service.submit(AuthenticatedAccount.idFrom(token), request.title(), request.problem(), request.solution());
    }

    @PostMapping("/innovation/ideas/{ideaId}/withdraw")
    @PreAuthorize(SUBMIT)
    @Operation(summary = "Withdraw my idea", description = "While it is still under review.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public IdeaView withdraw(@PathVariable Long ideaId, @AuthenticationPrincipal Jwt token) {
        return service.withdraw(AuthenticatedAccount.idFrom(token), ideaId);
    }

    @GetMapping("/innovation/reviews")
    @PreAuthorize(REVIEW)
    @Operation(summary = "Ideas to review", description = "Open ideas other than my own, each with my review if I gave one.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<IdeaView> forReview(@AuthenticationPrincipal Jwt token) {
        return service.forReview(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/innovation/ideas/{ideaId}/review")
    @PreAuthorize(REVIEW)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Review an idea", description = "One score from 1 to 5 and a comment, replaced while the idea is open.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void review(@PathVariable Long ideaId, @Valid @RequestBody ReviewRequest request, @AuthenticationPrincipal Jwt token) {
        service.review(AuthenticatedAccount.idFrom(token), ideaId, request.score(), request.comment());
    }

    @GetMapping("/admin/innovation/ideas")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Every idea with its reviews", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<IdeaView> all() {
        return service.all();
    }

    @PostMapping("/admin/innovation/ideas/{ideaId}/decision")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Decide an idea", description = "Shortlist it, or accept it or mark it not selected; decisions move forward only.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Already decided or withdrawn.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public void decide(@PathVariable Long ideaId, @Valid @RequestBody DecisionRequest request) {
        service.decide(ideaId, request.status(), request.note());
    }
}
