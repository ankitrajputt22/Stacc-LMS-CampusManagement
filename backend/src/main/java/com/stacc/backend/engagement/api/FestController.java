package com.stacc.backend.engagement.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.engagement.fests.FestService;
import com.stacc.backend.engagement.fests.FestService.ApplicationView;
import com.stacc.backend.engagement.fests.FestService.FestInput;
import com.stacc.backend.engagement.fests.FestService.FestView;
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

/** Fests: published fests with their schedules and volunteer shifts, and the campus office's management of them. */
@RestController
@RequestMapping("/api/v1/engagement")
@Tag(name = "Campus engagement")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class FestController {

    private static final String USE = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('CAMPUS_ENGAGEMENT_USE')";
    private static final String MANAGE = "hasRole('ADMIN') and hasAuthority('CAMPUS_ENGAGEMENT_MANAGE')";

    private final FestService service;

    public FestController(FestService service) {
        this.service = service;
    }

    public record FestRequest(@NotBlank @Size(max = 150) String name, @Size(max = 10_000) String description,
            @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {

        FestInput input() {
            return new FestInput(name, description, startsOn, endsOn);
        }
    }

    public record EventRequest(@NotNull Long eventId) {
    }

    public record ShiftRequest(@NotBlank @Size(max = 150) String roleTitle, @NotNull Instant startsAt, @NotNull Instant endsAt,
            @Min(1) @Max(500) int places) {
    }

    public record ApplyRequest(@Size(max = 500) String note) {
    }

    public record DecisionRequest(@NotNull Boolean accept) {
    }

    @GetMapping("/fests")
    @PreAuthorize(USE)
    @Operation(summary = "Published fests", description = "Each with its schedule, its volunteer shifts with places taken, and my applications.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<FestView> published(@AuthenticationPrincipal Jwt token) {
        return service.published(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/fests/shifts/{shiftId}/apply")
    @PreAuthorize(USE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply to volunteer for a shift", description = "Before it starts, once; the campus office accepts or declines.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Already applied, or the shift has started.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public FestView apply(@PathVariable Long shiftId, @Valid @RequestBody ApplyRequest request, @AuthenticationPrincipal Jwt token) {
        return service.apply(AuthenticatedAccount.idFrom(token), shiftId, request.note());
    }

    @PostMapping("/fests/applications/{applicationId}/withdraw")
    @PreAuthorize(USE)
    @Operation(summary = "Withdraw my volunteer application", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public FestView withdraw(@PathVariable Long applicationId, @AuthenticationPrincipal Jwt token) {
        return service.withdraw(AuthenticatedAccount.idFrom(token), applicationId);
    }

    @GetMapping("/admin/fests")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Every fest", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<FestView> all() {
        return service.all();
    }

    @PostMapping("/admin/fests")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a fest", description = "It starts as a draft.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public FestView create(@Valid @RequestBody FestRequest request, @AuthenticationPrincipal Jwt token) {
        return service.create(AuthenticatedAccount.idFrom(token), request.input());
    }

    @PutMapping("/admin/fests/{festId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change a fest", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public FestView edit(@PathVariable Long festId, @Valid @RequestBody FestRequest request) {
        return service.edit(festId, request.input());
    }

    @PostMapping("/admin/fests/{festId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Publish a fest", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public FestView publish(@PathVariable Long festId) {
        return service.publish(festId);
    }

    @PostMapping("/admin/fests/{festId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Archive a fest", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public FestView archive(@PathVariable Long festId) {
        return service.archive(festId);
    }

    @PostMapping("/admin/fests/{festId}/schedule")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Put an event on a fest's schedule", description = "An event belongs to at most one fest.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "The event is already on a fest, or the fest is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public FestView schedule(@PathVariable Long festId, @Valid @RequestBody EventRequest request) {
        return service.schedule(festId, request.eventId());
    }

    @PostMapping("/admin/fests/{festId}/unschedule")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Take an event off a fest's schedule", description = "The event itself stays.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public FestView unschedule(@PathVariable Long festId, @Valid @RequestBody EventRequest request) {
        return service.unschedule(festId, request.eventId());
    }

    @PostMapping("/admin/fests/{festId}/shifts")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a volunteer shift", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public FestView addShift(@PathVariable Long festId, @Valid @RequestBody ShiftRequest request) {
        return service.addShift(festId, request.roleTitle(), request.startsAt(), request.endsAt(), request.places());
    }

    @GetMapping("/admin/fests/{festId}/applications")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Volunteer applications of a fest", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<ApplicationView> applications(@PathVariable Long festId) {
        return service.applications(festId);
    }

    @PostMapping("/admin/fests/applications/{applicationId}/decision")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Accept or decline a volunteer", description = "Accepting needs a free place in the shift.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "The shift is full, or the application was already decided.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApplicationView decide(@PathVariable Long applicationId, @Valid @RequestBody DecisionRequest request) {
        return service.decide(applicationId, request.accept());
    }
}
