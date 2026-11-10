package com.stacc.backend.engagement.api;

import java.util.List;
import java.util.Set;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.events.CampusEventRegistration;
import com.stacc.backend.engagement.events.CampusEventService.Viewer;
import com.stacc.backend.engagement.events.EventAttendanceService;
import com.stacc.backend.engagement.events.EventAttendanceService.Attendee;
import com.stacc.backend.engagement.events.EventAttendanceService.CheckInCode;
import com.stacc.backend.engagement.events.EventAttendanceService.CheckInResult;
import com.stacc.backend.engagement.events.EventAttendanceService.MyAttendance;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Event attendance: attendees' check-in codes, check-in by code or college ID, and certificate eligibility. */
@RestController
@RequestMapping("/api/v1/engagement/events")
@Tag(name = "Campus engagement")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class EventAttendanceController {

    private static final String USE = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('CAMPUS_ENGAGEMENT_USE')";
    private static final String MANAGE = "hasRole('ADMIN') and hasAuthority('CAMPUS_ENGAGEMENT_MANAGE')";
    private static final String ANY = "(" + USE + ") or (" + MANAGE + ")";

    private final EventAttendanceService service;

    public EventAttendanceController(EventAttendanceService service) {
        this.service = service;
    }

    public record TrialOutcomeRequest(@NotBlank @Size(max = 100) String loginId, @NotNull CampusEventRegistration.TrialOutcome outcome) {
    }

    public record CheckInRequest(@Size(max = 40) String code, @Size(max = 100) String loginId) {
    }

    private static Viewer viewer(Jwt token, Authentication authentication) {
        Set<String> held = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        return new Viewer(AuthenticatedAccount.idFrom(token), held.contains("ROLE_STUDENT"), held.contains("ROLE_FACULTY"),
                held.contains("ROLE_ADMIN") && held.contains("CAMPUS_ENGAGEMENT_MANAGE"));
    }

    @PostMapping("/{eventId}/check-in-code")
    @PreAuthorize(USE)
    @Operation(summary = "My check-in code", description = "A fresh code for my registration, shown once; earlier codes stop working.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Not registered (a waitlisted place has no code).",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CheckInCode myCode(@PathVariable Long eventId, @AuthenticationPrincipal Jwt token) {
        return service.myCode(AuthenticatedAccount.idFrom(token), eventId);
    }

    @GetMapping("/{eventId}/my-attendance")
    @PreAuthorize(USE)
    @Operation(summary = "My attendance", description = "Whether I am registered, when I checked in, and whether a participation certificate is due.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public MyAttendance mine(@PathVariable Long eventId, @AuthenticationPrincipal Jwt token) {
        return service.mine(AuthenticatedAccount.idFrom(token), eventId);
    }

    @GetMapping("/{eventId}/attendance")
    @PreAuthorize(ANY)
    @Operation(summary = "Attendance of an event", description = "For those who manage it: registrations with check-in time and certificate eligibility.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<Attendee> attendance(@PathVariable Long eventId, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        return service.attendees(viewer(token, authentication), eventId);
    }

    @PostMapping("/{eventId}/check-ins")
    @PreAuthorize(ANY)
    @Operation(summary = "Check an attendee in", description = "By their check-in code or college ID, from an hour before the event until it ends. Again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "404", description = "Not an event the account manages, an unknown code, or no registration.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Outside the check-in window, or a waitlisted place.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CheckInResult checkIn(@PathVariable Long eventId, @Valid @RequestBody CheckInRequest request, @AuthenticationPrincipal Jwt token,
            Authentication authentication) {
        Viewer viewer = viewer(token, authentication);
        if (request.code() != null && !request.code().isBlank()) {
            return service.checkInByCode(viewer, eventId, request.code());
        }
        if (request.loginId() != null && !request.loginId().isBlank()) {
            return service.checkInByLoginId(viewer, eventId, request.loginId());
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a check-in code or a college ID.");
    }

    @PostMapping("/{eventId}/trial")
    @PreAuthorize(ANY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Make an event a trial", description = "Before it starts; it stays a trial. Again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "The event has started or was cancelled.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public void markAsTrial(@PathVariable Long eventId, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        service.markAsTrial(viewer(token, authentication), eventId);
    }

    @PostMapping("/{eventId}/trial-outcomes")
    @PreAuthorize(ANY)
    @Operation(summary = "Record a trial outcome", description = "Selected or Not selected for a registered attendee, once the trial has started; it may be corrected.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "404", description = "Not an event the account manages, or no registration.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Not a trial, not started yet, or a waitlisted place.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public Attendee decideTrial(@PathVariable Long eventId, @Valid @RequestBody TrialOutcomeRequest request, @AuthenticationPrincipal Jwt token,
            Authentication authentication) {
        return service.decideTrial(viewer(token, authentication), eventId, request.loginId(), request.outcome());
    }
}
