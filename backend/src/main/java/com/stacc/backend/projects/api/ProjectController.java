package com.stacc.backend.projects.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.projects.Project;
import com.stacc.backend.projects.ProjectService;
import com.stacc.backend.projects.ProjectService.ProjectView;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Student projects: one team model with join requests decided by the lead and faculty mentors who accept an invitation. */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "No such project, place, or invitation for the signed-in account.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class ProjectController {

    private static final String ANY = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('PROJECT_USE')";
    private static final String STUDENT = "hasRole('STUDENT') and hasAuthority('PROJECT_USE')";
    private static final String FACULTY = "hasRole('FACULTY') and hasAuthority('PROJECT_USE')";

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    public record ProjectRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = Project.MAX_SUMMARY) String summary,
            @Min(1) @Max(20) int maxMembers) {
    }

    public record StatusRequest(@NotNull Project.Status status) {
    }

    public record JoinRequest(@Size(max = 500) String message) {
    }

    public record DecisionRequest(@NotNull Boolean accept) {
    }

    public record MentorRequest(@NotBlank @Size(max = 100) String loginId) {
    }

    @GetMapping
    @PreAuthorize(ANY)
    @Operation(summary = "Projects", description = "Every project that is not archived, with members and mentors by login ID.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<ProjectView> list(@AuthenticationPrincipal Jwt token) {
        return service.list(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a project", description = "The student leads it and counts as its first member.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ProjectView create(@Valid @RequestBody ProjectRequest request, @AuthenticationPrincipal Jwt token) {
        return service.create(AuthenticatedAccount.idFrom(token), request.title(), request.summary(), request.maxMembers());
    }

    @PutMapping("/{projectId}")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Change a project", description = "The lead only; the limit never goes below the current members.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ProjectView edit(@PathVariable Long projectId, @Valid @RequestBody ProjectRequest request, @AuthenticationPrincipal Jwt token) {
        return service.edit(AuthenticatedAccount.idFrom(token), projectId, request.title(), request.summary(), request.maxMembers());
    }

    @PostMapping("/{projectId}/status")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Open, close, or archive a project", description = "The lead only; an archived project no longer changes.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ProjectView status(@PathVariable Long projectId, @Valid @RequestBody StatusRequest request, @AuthenticationPrincipal Jwt token) {
        return service.setStatus(AuthenticatedAccount.idFrom(token), projectId, request.status());
    }

    @PostMapping("/{projectId}/join")
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask to join a project", description = "An open project, once while the request or place is live.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Not open, or already asked or on the project.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ProjectView join(@PathVariable Long projectId, @Valid @RequestBody JoinRequest request, @AuthenticationPrincipal Jwt token) {
        return service.requestToJoin(AuthenticatedAccount.idFrom(token), projectId, request.message());
    }

    @PostMapping("/members/{memberId}/decision")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Accept or decline a join request", description = "The lead only; accepting needs a free place.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "The project is full, or the request was already decided.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ProjectView decide(@PathVariable Long memberId, @Valid @RequestBody DecisionRequest request, @AuthenticationPrincipal Jwt token) {
        return service.decide(AuthenticatedAccount.idFrom(token), memberId, request.accept());
    }

    @PostMapping("/members/{memberId}/leave")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Withdraw my request or leave", description = "The lead stays with the project.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ProjectView leave(@PathVariable Long memberId, @AuthenticationPrincipal Jwt token) {
        return service.leave(AuthenticatedAccount.idFrom(token), memberId);
    }

    @PostMapping("/{projectId}/mentors")
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invite a faculty mentor", description = "The lead only; an active faculty account.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ProjectView invite(@PathVariable Long projectId, @Valid @RequestBody MentorRequest request, @AuthenticationPrincipal Jwt token) {
        return service.inviteMentor(AuthenticatedAccount.idFrom(token), projectId, request.loginId());
    }

    @PostMapping("/mentors/{mentorId}/answer")
    @PreAuthorize(FACULTY)
    @Operation(summary = "Accept or decline a mentoring invitation", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ProjectView answer(@PathVariable Long mentorId, @Valid @RequestBody DecisionRequest request, @AuthenticationPrincipal Jwt token) {
        return service.answerMentorship(AuthenticatedAccount.idFrom(token), mentorId, request.accept());
    }

    @PostMapping("/mentors/{mentorId}/step-down")
    @PreAuthorize(FACULTY)
    @Operation(summary = "Stop mentoring a project", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ProjectView stepDown(@PathVariable Long mentorId, @AuthenticationPrincipal Jwt token) {
        return service.answerMentorship(AuthenticatedAccount.idFrom(token), mentorId, null);
    }
}
