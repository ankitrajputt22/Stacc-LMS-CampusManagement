package com.stacc.backend.hostel.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelWardenService;
import com.stacc.backend.hostel.outing.GateMovement;
import com.stacc.backend.hostel.outing.HostelComplaintService;
import com.stacc.backend.hostel.outing.HostelOutingService;
import com.stacc.backend.hostel.outing.HostelOutingService.MovementView;
import com.stacc.backend.hostel.outing.HostelOutingService.OutingView;
import com.stacc.backend.hostel.outing.HostelOutingService.PassCheck;
import com.stacc.backend.hostel.outing.HostelOutingService.PassView;
import com.stacc.backend.hostel.outing.OutingKind;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketDetail;
import com.stacc.backend.support.helpdesk.HelpdeskTicketPriority;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hostel outings and the gate: residents ask for gate passes and leave, extend leave, open their pass, and report room
 * problems; security checks pass codes and records movements; wardens see overdue students of their hostels. Approval
 * decisions go through the approval task routes.
 */
@RestController
@Tag(name = "Hostel outings and gate")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HostelOutingController {

    private static final String STUDENT = "hasRole('STUDENT') and hasAuthority('HOSTEL_OUTING_REQUEST')";
    private static final String SECURITY = "hasRole('SECURITY') and hasAuthority('GATE_PASS_VERIFY')";

    public record OutingRequest(@NotNull OutingKind kind, @NotNull Instant leaveFrom, @NotNull Instant leaveUntil,
            @NotBlank @Size(max = 1000) String reason, @Size(max = 200) String destination) {
    }

    public record ExtensionRequest(@NotNull Instant until, @NotBlank @Size(max = 1000) String reason) {
    }

    public record CodeRequest(@NotBlank @Size(max = 40) String code) {
    }

    public record MovementRequest(@NotBlank @Size(max = 40) String code, @NotNull GateMovement.Direction direction) {
    }

    public record ComplaintRequest(@NotBlank @Size(max = 200) String subject, @NotBlank @Size(max = 9000) String description,
            HelpdeskTicketPriority priority) {
    }

    private final HostelOutingService outings;
    private final HostelComplaintService complaints;
    private final HostelWardenService wardens;
    private final HostelAllocationRepository allocations;

    public HostelOutingController(HostelOutingService outings, HostelComplaintService complaints, HostelWardenService wardens,
            HostelAllocationRepository allocations) {
        this.outings = outings;
        this.complaints = complaints;
        this.wardens = wardens;
        this.allocations = allocations;
    }

    @PostMapping("/api/v1/hostel/student/outings")
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask for a gate pass or leave",
            description = "For current hostel residents. A gate pass lasts at most 24 hours, leave at most 60 days. Times are "
                    + "ISO 8601 with a zone. The student's hostel warden approves it.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public OutingView request(@AuthenticationPrincipal Jwt token, @Valid @RequestBody OutingRequest request) {
        return outings.request(AuthenticatedAccount.idFrom(token), request.kind(), request.leaveFrom(), request.leaveUntil(),
                request.reason(), request.destination());
    }

    @GetMapping("/api/v1/hostel/student/outings")
    @PreAuthorize(STUDENT)
    @Operation(summary = "List your outings", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<OutingView> mine(@AuthenticationPrincipal Jwt token) {
        return outings.mine(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/api/v1/hostel/student/outings/{outingId}/cancel")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Cancel your outing request while it waits for approval", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public OutingView cancel(@AuthenticationPrincipal Jwt token, @PathVariable Long outingId) {
        return outings.cancel(AuthenticatedAccount.idFrom(token), outingId);
    }

    @PostMapping("/api/v1/hostel/student/outings/{outingId}/extensions")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Ask to extend approved leave", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public OutingView extend(@AuthenticationPrincipal Jwt token, @PathVariable Long outingId, @Valid @RequestBody ExtensionRequest request) {
        return outings.extend(AuthenticatedAccount.idFrom(token), outingId, request.until(), request.reason());
    }

    @PostMapping("/api/v1/hostel/student/outings/{outingId}/pass")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Open the pass of your approved outing",
            description = "Returns a fresh code to show at the gate as a QR code; earlier codes for the outing stop working.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public PassView pass(@AuthenticationPrincipal Jwt token, @PathVariable Long outingId) {
        return outings.pass(AuthenticatedAccount.idFrom(token), outingId);
    }

    @PostMapping("/api/v1/hostel/student/complaints")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('HELPDESK_TICKET_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a problem in your hostel room",
            description = "Creates a helpdesk ticket of the HOSTEL category with your current room attached.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public HelpdeskTicketDetail complain(@AuthenticationPrincipal Jwt token, Authentication authentication,
            @Valid @RequestBody ComplaintRequest request) {
        return complaints.complain(AuthenticatedAccount.idFrom(token), authentication.getAuthorities(), request.subject(),
                request.description(), request.priority());
    }

    @PostMapping("/api/v1/gate/check")
    @PreAuthorize(SECURITY)
    @Operation(summary = "Check a pass code", description = "Says whether the pass lets the student out or back now. Only reads.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public PassCheck check(@Valid @RequestBody CodeRequest request) {
        return outings.check(request.code());
    }

    @PostMapping("/api/v1/gate/movements")
    @PreAuthorize(SECURITY)
    @Operation(summary = "Record a student going out or coming back",
            description = "Out needs a valid pass and a student inside; back needs a student outside on that outing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public PassCheck move(@AuthenticationPrincipal Jwt token, @Valid @RequestBody MovementRequest request) {
        return outings.record(AuthenticatedAccount.idFrom(token), request.code(), request.direction());
    }

    @GetMapping("/api/v1/gate/movements")
    @PreAuthorize(SECURITY)
    @Operation(summary = "The latest gate movements", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<MovementView> movements() {
        return outings.recentMovements();
    }

    @GetMapping("/api/v1/gate/students/{loginId}/outings")
    @PreAuthorize(SECURITY)
    @Operation(summary = "Look a student up by login ID", description = "Their latest outings and whether they are outside.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<OutingView> lookup(@PathVariable String loginId) {
        return outings.lookup(loginId);
    }

    @GetMapping("/api/v1/hostel/warden/overdue")
    @PreAuthorize(HostelAccess.WARDEN)
    @Operation(summary = "Students of your hostels who are overdue",
            description = "Approved outings past their end while the student is still outside, worked out now.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<OutingView> overdue(@AuthenticationPrincipal Jwt token) {
        List<Long> students = wardens.hostelsOf(AuthenticatedAccount.idFrom(token)).stream()
                .flatMap(hostelId -> allocations.findActiveInHostel(hostelId).stream())
                .map(allocation -> allocation.getHostelApplication().getStudentProfile().getUserAccount().getId())
                .distinct().toList();
        return outings.overdue(students);
    }
}
