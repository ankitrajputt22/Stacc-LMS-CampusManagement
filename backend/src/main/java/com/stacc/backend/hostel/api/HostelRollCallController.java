package com.stacc.backend.hostel.api;

import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.hostel.rollcall.HostelRollCallEntry;
import com.stacc.backend.hostel.rollcall.HostelRollCallService;
import com.stacc.backend.hostel.rollcall.HostelRollCallService.Detail;
import com.stacc.backend.hostel.rollcall.HostelRollCallService.Mark;
import com.stacc.backend.hostel.rollcall.HostelRollCallService.Summary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/** Night roll calls of a warden's own hostels: open a sheet, mark residents, finalize. Other hostels answer 404. */
@RestController
@RequestMapping("/api/v1/hostel/warden")
@Tag(name = "Hostel roll calls")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The hostel or roll call is not one of the warden's. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HostelRollCallController {

    private static final String WARDEN = "hasRole('WARDEN') and hasAuthority('HOSTEL_ROLL_CALL')";

    private final HostelRollCallService service;

    public HostelRollCallController(HostelRollCallService service) {
        this.service = service;
    }

    public record OpenRequest(@Size(max = 50) String block, @Schema(description = "The night; today when empty.") LocalDate night) {
    }

    public record MarkRequest(@NotNull Long entryId, @NotNull HostelRollCallEntry.Status status) {
    }

    public record MarksRequest(@NotNull @Size(min = 1, max = 500) List<@NotNull @Valid MarkRequest> marks) {
    }

    @GetMapping("/hostels/{hostelId}/roll-calls")
    @PreAuthorize(WARDEN)
    @Operation(summary = "Recent roll calls of a hostel", description = "Newest night first, with counts.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<Summary> recent(@PathVariable Long hostelId, @AuthenticationPrincipal Jwt token) {
        return service.recent(AuthenticatedAccount.idFrom(token), hostelId);
    }

    @PostMapping("/hostels/{hostelId}/roll-calls")
    @PreAuthorize(WARDEN)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a night's roll call",
            description = "Lists the residents allocated in the hostel, or the block, now. Those whose last gate movement was going out start as OUT, everyone else as UNMARKED; nobody starts as ABSENT.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The sheet.")
    @ApiResponse(responseCode = "409", description = "There is already a roll call for that hostel or block and night.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public Detail open(@PathVariable Long hostelId, @Valid @RequestBody OpenRequest request, @AuthenticationPrincipal Jwt token) {
        return service.open(AuthenticatedAccount.idFrom(token), hostelId, request.block(), request.night());
    }

    @GetMapping("/roll-calls/{rollCallId}")
    @PreAuthorize(WARDEN)
    @Operation(summary = "Read a roll call", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public Detail detail(@PathVariable Long rollCallId, @AuthenticationPrincipal Jwt token) {
        return service.detail(AuthenticatedAccount.idFrom(token), rollCallId);
    }

    @PutMapping("/roll-calls/{rollCallId}/marks")
    @PreAuthorize(WARDEN)
    @Operation(summary = "Mark residents", description = "PRESENT, ABSENT, or OUT, while the roll call is open.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "400", description = "A mark names no resident of this roll call, or tries to unmark.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The roll call is finalized.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public Detail mark(@PathVariable Long rollCallId, @Valid @RequestBody MarksRequest request, @AuthenticationPrincipal Jwt token) {
        return service.mark(AuthenticatedAccount.idFrom(token), rollCallId,
                request.marks().stream().map(mark -> new Mark(mark.entryId(), mark.status())).toList());
    }

    @PostMapping("/roll-calls/{rollCallId}/finalize")
    @PreAuthorize(WARDEN)
    @Operation(summary = "Finalize a roll call", description = "Every resident must be marked. It then no longer changes.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "A resident is still unmarked.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public Detail finalizeRollCall(@PathVariable Long rollCallId, @AuthenticationPrincipal Jwt token) {
        return service.finalizeRollCall(AuthenticatedAccount.idFrom(token), rollCallId);
    }
}
