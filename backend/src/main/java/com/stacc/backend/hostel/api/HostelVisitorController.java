package com.stacc.backend.hostel.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.hostel.visitor.HostelVisit;
import com.stacc.backend.hostel.visitor.HostelVisitorService;
import com.stacc.backend.hostel.visitor.HostelVisitorService.HoursView;
import com.stacc.backend.hostel.visitor.HostelVisitorService.VisitView;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Hostel visitors: residents register them, security records them at the gate, the hostel office sets visiting hours. */
@RestController
@Tag(name = "Hostel visitors")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HostelVisitorController {

    private static final String RESIDENT = "hasRole('STUDENT') and hasAuthority('HOSTEL_VISITOR_REGISTER')";
    private static final String DESK = "hasRole('SECURITY') and hasAuthority('HOSTEL_VISITOR_DESK')";

    private final HostelVisitorService service;

    public HostelVisitorController(HostelVisitorService service) {
        this.service = service;
    }

    public record RegisterRequest(@NotBlank @Size(max = HostelVisit.MAX_NAME_LENGTH) String visitorName,
            @NotBlank @Size(max = HostelVisit.MAX_RELATION_LENGTH) String relation, @NotNull LocalDate expectedOn) {
    }

    public record WalkInRequest(@NotBlank @Size(max = 100) String residentLoginId,
            @NotBlank @Size(max = HostelVisit.MAX_NAME_LENGTH) String visitorName,
            @NotBlank @Size(max = HostelVisit.MAX_RELATION_LENGTH) String relation) {
    }

    public record HoursRequest(@NotNull @Schema(example = "16:00") LocalTime opensAt, @NotNull @Schema(example = "19:00") LocalTime closesAt) {
    }

    @GetMapping("/api/v1/hostel/student/visitors")
    @PreAuthorize(RESIDENT)
    @Operation(summary = "My visitors", description = "From 30 days ago on, newest first.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<VisitView> mine(@AuthenticationPrincipal Jwt token) {
        return service.mine(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/api/v1/hostel/student/visitors")
    @PreAuthorize(RESIDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a visitor", description = "For today or a day in the next 30 days. Only the name and relation are kept.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "The student has no hostel bed.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public VisitView register(@Valid @RequestBody RegisterRequest request, @AuthenticationPrincipal Jwt token) {
        return service.register(AuthenticatedAccount.idFrom(token), request.visitorName(), request.relation(), request.expectedOn());
    }

    @PostMapping("/api/v1/hostel/student/visitors/{visitId}/cancel")
    @PreAuthorize(RESIDENT)
    @Operation(summary = "Cancel a registered visitor", description = "While the visitor is still expected.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public VisitView cancel(@PathVariable Long visitId, @AuthenticationPrincipal Jwt token) {
        return service.cancel(AuthenticatedAccount.idFrom(token), visitId);
    }

    @GetMapping("/api/v1/gate/visitors")
    @PreAuthorize(DESK)
    @Operation(summary = "Today's visitors", description = "Expected, inside, and gone, at every hostel.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<VisitView> today() {
        return service.today();
    }

    @PostMapping("/api/v1/gate/visitors/{visitId}/arrive")
    @PreAuthorize(DESK)
    @Operation(summary = "Record a visitor's arrival", description = "Only today's expected visitors, within the hostel's visiting hours.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Outside visiting hours, another day, or not expected any more.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public VisitView arrive(@PathVariable Long visitId, @AuthenticationPrincipal Jwt token) {
        return service.arrive(AuthenticatedAccount.idFrom(token), visitId);
    }

    @PostMapping("/api/v1/gate/visitors/{visitId}/leave")
    @PreAuthorize(DESK)
    @Operation(summary = "Record a visitor leaving", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public VisitView leave(@PathVariable Long visitId) {
        return service.leave(visitId);
    }

    @PostMapping("/api/v1/gate/visitors/walk-in")
    @PreAuthorize(DESK)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a walk-in visitor", description = "For the resident with this college ID, within visiting hours.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "404", description = "No hostel resident has that college ID.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public VisitView walkIn(@Valid @RequestBody WalkInRequest request, @AuthenticationPrincipal Jwt token) {
        return service.walkIn(AuthenticatedAccount.idFrom(token), request.residentLoginId(), request.visitorName(), request.relation());
    }

    @GetMapping("/api/v1/hostel/admin/hostels/{hostelId}/visiting-hours")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "A hostel's visiting hours", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "No visiting hours set: the hostel takes no visitors.")
    public ResponseEntity<HoursView> hours(@PathVariable Long hostelId) {
        return service.hoursOf(hostelId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/api/v1/hostel/admin/hostels/{hostelId}/visiting-hours")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Set a hostel's visiting hours", description = "Daily, in the college's time zone.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public HoursView setHours(@PathVariable Long hostelId, @Valid @RequestBody HoursRequest request) {
        return service.setHours(hostelId, request.opensAt(), request.closesAt());
    }
}
