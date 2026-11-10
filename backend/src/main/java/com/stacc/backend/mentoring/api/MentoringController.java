package com.stacc.backend.mentoring.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.mentoring.MentoringActionItem;
import com.stacc.backend.mentoring.MentoringMeeting;
import com.stacc.backend.mentoring.MentoringService;
import com.stacc.backend.mentoring.MentoringService.PairingDetail;
import com.stacc.backend.mentoring.MentoringService.PairingView;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mentoring: the pair's meetings, notes, and action items, and the admins' pairings. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Mentoring")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "Not a pairing, meeting, or item of the signed-in account.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class MentoringController {

    private static final String USE = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('MENTORING_USE')";
    private static final String MANAGE = "hasRole('ADMIN') and hasAuthority('MENTORING_MANAGE')";

    private final MentoringService service;

    public MentoringController(MentoringService service) {
        this.service = service;
    }

    public record MeetingRequest(@NotNull Instant heldAt, @NotBlank @Size(max = 200) String title,
            @Size(max = MentoringMeeting.MAX_NOTES) String sharedNotes, @Size(max = MentoringMeeting.MAX_NOTES) String privateNotes) {
    }

    public record ActionItemRequest(@NotBlank @Size(max = 500) String description, @NotNull MentoringActionItem.Owner owner, Instant dueAt) {
    }

    public record PairRequest(@NotBlank @Size(max = 100) String mentorLoginId, @NotBlank @Size(max = 100) String menteeLoginId) {
    }

    @GetMapping("/mentoring/me")
    @PreAuthorize(USE)
    @Operation(summary = "My mentoring pairings", description = "As mentor or as student, active first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<PairingView> mine(@AuthenticationPrincipal Jwt token) {
        return service.pairings(AuthenticatedAccount.idFrom(token));
    }

    @GetMapping("/mentoring/pairings/{assignmentId}")
    @PreAuthorize(USE)
    @Operation(summary = "A pairing's meetings and action items", description = "Private notes reach the mentor only.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public PairingDetail pairing(@PathVariable Long assignmentId, @AuthenticationPrincipal Jwt token) {
        return service.pairing(AuthenticatedAccount.idFrom(token), assignmentId);
    }

    @PostMapping("/mentoring/pairings/{assignmentId}/meetings")
    @PreAuthorize(USE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a meeting", description = "The mentor of an active pairing only.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public PairingDetail addMeeting(@PathVariable Long assignmentId, @Valid @RequestBody MeetingRequest request, @AuthenticationPrincipal Jwt token) {
        return service.addMeeting(AuthenticatedAccount.idFrom(token), assignmentId, request.heldAt(), request.title(), request.sharedNotes(),
                request.privateNotes());
    }

    @PutMapping("/mentoring/meetings/{meetingId}")
    @PreAuthorize(USE)
    @Operation(summary = "Edit a meeting", description = "The mentor of an active pairing only.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public PairingDetail editMeeting(@PathVariable Long meetingId, @Valid @RequestBody MeetingRequest request, @AuthenticationPrincipal Jwt token) {
        return service.editMeeting(AuthenticatedAccount.idFrom(token), meetingId, request.heldAt(), request.title(), request.sharedNotes(),
                request.privateNotes());
    }

    @PostMapping("/mentoring/meetings/{meetingId}/action-items")
    @PreAuthorize(USE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an action item", description = "For the mentor or the student; it appears on the owner's To-Do list until done.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public PairingDetail addActionItem(@PathVariable Long meetingId, @Valid @RequestBody ActionItemRequest request, @AuthenticationPrincipal Jwt token) {
        return service.addActionItem(AuthenticatedAccount.idFrom(token), meetingId, request.description(), request.owner(), request.dueAt());
    }

    @PostMapping("/mentoring/action-items/{itemId}/complete")
    @PreAuthorize(USE)
    @Operation(summary = "Mark an action item done", description = "Either of the pair; again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public PairingDetail complete(@PathVariable Long itemId, @AuthenticationPrincipal Jwt token) {
        return service.completeActionItem(AuthenticatedAccount.idFrom(token), itemId);
    }

    @GetMapping("/admin/mentoring/pairings")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Every mentoring pairing", description = "Who mentors whom; meetings stay with the pair.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<PairingView> all() {
        return service.allPairings();
    }

    @PostMapping("/admin/mentoring/pairings")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pair a mentor with a student", description = "An active faculty account and an active student account.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Not eligible accounts, or already paired.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PairingView pair(@Valid @RequestBody PairRequest request) {
        return service.pair(request.mentorLoginId(), request.menteeLoginId());
    }

    @PostMapping("/admin/mentoring/pairings/{assignmentId}/end")
    @PreAuthorize(MANAGE)
    @Operation(summary = "End a pairing", description = "Its meetings stay readable by the pair.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public PairingView end(@PathVariable Long assignmentId) {
        return service.end(assignmentId);
    }
}
