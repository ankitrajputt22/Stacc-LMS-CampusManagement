package com.stacc.backend.hostel.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.hostel.HostelRoomChange;
import com.stacc.backend.hostel.HostelRoomChangeService;
import com.stacc.backend.hostel.HostelRoomChangeService.RoomChangeView;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Room changes: residents ask to move or propose a swap and answer swaps proposed to them; the hostel office carries
 * out approved changes. The warden decides through the approval task routes; this controller never decides.
 */
@RestController
@Tag(name = "Hostel room changes")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HostelRoomChangeController {

    private static final String RESIDENT = "hasRole('STUDENT') and hasAuthority('HOSTEL_ROOM_CHANGE_REQUEST')";
    private static final String STUDENT_PATH = "/api/v1/hostel/student/room-changes";

    private final HostelRoomChangeService service;

    public HostelRoomChangeController(HostelRoomChangeService service) {
        this.service = service;
    }

    public record MoveRequest(@NotBlank @Size(max = HostelRoomChange.MAX_REASON_LENGTH) String reason,
            @Size(max = HostelRoomChange.MAX_PREFERENCE_LENGTH) String preference) {
    }

    public record SwapRequest(@NotBlank @Size(max = 100) String partnerLoginId,
            @NotBlank @Size(max = HostelRoomChange.MAX_REASON_LENGTH) String reason) {
    }

    public record AnswerRequest(@NotNull Boolean accept) {
    }

    public record CompleteRequest(@Schema(description = "The bed to move to; needed for a move, ignored for a swap.") Long bedId) {
    }

    @GetMapping(STUDENT_PATH)
    @PreAuthorize(RESIDENT)
    @Operation(summary = "My room changes", description = "Those I asked for and swaps proposed to me, newest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<RoomChangeView> mine(@AuthenticationPrincipal Jwt token) {
        return service.mine(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping(STUDENT_PATH + "/move")
    @PreAuthorize(RESIDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask to move to another bed",
            description = "Goes to the hostel warden for approval. A preference is only a wish: the hostel office chooses the bed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The room change.")
    @ApiResponse(responseCode = "409", description = "No hostel bed, an open room change already, or no warden to decide.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public RoomChangeView move(@Valid @RequestBody MoveRequest request, @AuthenticationPrincipal Jwt token) {
        return service.requestMove(AuthenticatedAccount.idFrom(token), request.reason(), request.preference());
    }

    @PostMapping(STUDENT_PATH + "/swap")
    @PreAuthorize(RESIDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Propose a bed swap with another resident",
            description = "By their college ID. They accept or decline first; an accepted swap goes to the warden.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The room change.")
    @ApiResponse(responseCode = "409", description = "Either student has no hostel bed or an open room change already.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public RoomChangeView swap(@Valid @RequestBody SwapRequest request, @AuthenticationPrincipal Jwt token) {
        return service.proposeSwap(AuthenticatedAccount.idFrom(token), request.partnerLoginId(), request.reason());
    }

    @PostMapping(STUDENT_PATH + "/{changeId}/answer")
    @PreAuthorize(RESIDENT)
    @Operation(summary = "Accept or decline a swap proposed to me", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "404", description = "No such swap proposed to the account.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The swap was already answered, cancelled, or a bed changed meanwhile.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public RoomChangeView answer(@PathVariable Long changeId, @Valid @RequestBody AnswerRequest request, @AuthenticationPrincipal Jwt token) {
        return service.answerSwap(AuthenticatedAccount.idFrom(token), changeId, request.accept());
    }

    @PostMapping(STUDENT_PATH + "/{changeId}/cancel")
    @PreAuthorize(RESIDENT)
    @Operation(summary = "Cancel my room change", description = "While it waits for the partner or the warden.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "It was already decided or finished.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public RoomChangeView cancel(@PathVariable Long changeId, @AuthenticationPrincipal Jwt token) {
        return service.cancel(AuthenticatedAccount.idFrom(token), changeId);
    }

    @GetMapping("/api/v1/hostel/admin/room-changes")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Open room changes", description = "Oldest first, with the warden's decision as the approval engine has it.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<RoomChangeView> open() {
        return service.open();
    }

    @PostMapping("/api/v1/hostel/admin/room-changes/{changeId}/complete")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Carry out an approved room change",
            description = "A move vacates the student's bed and allocates the chosen bed; a swap gives each student the other's bed. All or nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Not approved, already finished, a bed changed meanwhile, or the chosen bed is not free.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public RoomChangeView complete(@PathVariable Long changeId, @Valid @RequestBody CompleteRequest request,
            @AuthenticationPrincipal Jwt token) {
        return service.complete(AuthenticatedAccount.idFrom(token), changeId, request.bedId());
    }
}
