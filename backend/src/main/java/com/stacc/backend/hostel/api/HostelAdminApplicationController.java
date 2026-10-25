package com.stacc.backend.hostel.api;

import java.util.List;

import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.stacc.backend.hostel.HostelAllocationService;
import com.stacc.backend.hostel.HostelAllocationStatus;
import com.stacc.backend.hostel.HostelQueryService;
import com.stacc.backend.hostel.HostelRequests.CreateHostelAllocationRequest;
import com.stacc.backend.hostel.HostelResponses.AvailableHostelBed;
import com.stacc.backend.hostel.HostelResponses.HostelAdminApplicationDetail;
import com.stacc.backend.hostel.HostelResponses.HostelAllocationPage;
import com.stacc.backend.hostel.HostelResponses.HostelAllocationResponse;
import com.stacc.backend.hostel.HostelResponses.HostelApplicationPage;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The hostel office's applications and allocations. Every route needs the admin role and HOSTEL_MANAGE. Approving or
 * rejecting an application is not here: the office uses the approval task routes with the step shown in the detail,
 * which check the step's role and APPROVAL_TASK_DECIDE. The acting admin always comes from the access token.
 */
@RestController
@RequestMapping("/api/hostel/admin")
@Tag(name = "Hostel admin")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HostelAdminApplicationController {

    private final HostelQueryService queries;
    private final HostelAllocationService allocations;

    public HostelAdminApplicationController(HostelQueryService queries, HostelAllocationService allocations) {
        this.queries = queries;
        this.allocations = allocations;
    }

    @GetMapping("/applications")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "List hostel applications",
            description = "One page of every application, newest first, with its approval status from the approval engine and its active allocation. Each filter that is sent narrows the list.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of applications. The list may be empty.")
    @ApiResponse(responseCode = "400", description = "A filter is not valid, the page is below 0, or the size is not between 1 and 100.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelApplicationPage applications(
            @Parameter(description = "Only applications for this session.") @RequestParam(required = false) Long academicSessionId,
            @Parameter(description = "Only applications whose approval is in this status.") @RequestParam(required = false) ApprovalRequestStatus approvalStatus,
            @Parameter(description = "Only this student's applications.") @RequestParam(required = false) String studentLoginId,
            @Parameter(description = "Only applications with (true) or without (false) an active allocation.") @RequestParam(required = false) Boolean hasActiveAllocation,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return queries.adminApplications(academicSessionId, approvalStatus, studentLoginId, hasActiveAllocation, page, size);
    }

    @GetMapping("/applications/{applicationId}")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Get one hostel application",
            description = "The application with its approval steps and history, the step waiting for a decision with whether the signed-in admin may decide it through the approval task routes, and every allocation.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The application.")
    @ApiResponse(responseCode = "404", description = "The application does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelAdminApplicationDetail application(@AuthenticationPrincipal Jwt accessToken,
            Authentication authentication, @PathVariable Long applicationId) {
        return queries.adminApplication(ApprovalActor.of(AuthenticatedAccount.idFrom(accessToken),
                authentication.getAuthorities()), applicationId);
    }

    @GetMapping("/available-beds")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "List available beds",
            description = "Active beds in active rooms of active hostels with no active allocation, by hostel, room, and bed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The beds. The list may be empty.")
    public List<AvailableHostelBed> availableBeds(
            @Parameter(description = "Only beds of this hostel.") @RequestParam(required = false) Long hostelId,
            @Parameter(description = "Only beds of this room.") @RequestParam(required = false) Long roomId) {
        return allocations.availableBeds(hostelId, roomId);
    }

    @PostMapping("/applications/{applicationId}/allocations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Allocate a bed",
            description = "Allocates an available bed to an approved application whose student has no active allocation. The student is told in their Inbox.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new active allocation.")
    @ApiResponse(responseCode = "400", description = "The bed is missing.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The application or bed does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The application is not approved, it or its student already has an active allocation, or the bed is inactive or no longer available.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelAllocationResponse allocate(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long applicationId,
            @Valid @RequestBody CreateHostelAllocationRequest request) {
        return allocations.allocate(AuthenticatedAccount.idFrom(accessToken), applicationId, request.bedId());
    }

    @GetMapping("/allocations")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "List hostel allocations", description = "One page of every allocation, newest first. Each filter that is sent narrows the list.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of allocations. The list may be empty.")
    @ApiResponse(responseCode = "400", description = "A filter is not valid, the page is below 0, or the size is not between 1 and 100.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelAllocationPage allocations(
            @Parameter(description = "ACTIVE or VACATED.") @RequestParam(required = false) HostelAllocationStatus status,
            @Parameter(description = "Only allocations in this hostel.") @RequestParam(required = false) Long hostelId,
            @Parameter(description = "Only allocations for this session.") @RequestParam(required = false) Long academicSessionId,
            @Parameter(description = "Only this student's allocations.") @RequestParam(required = false) String studentLoginId,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return queries.allocations(status, hostelId, academicSessionId, studentLoginId, page, size);
    }

    @PostMapping("/allocations/{allocationId}/vacate")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Vacate an allocation",
            description = "Ends an active allocation and keeps it as history; the bed becomes available again and the student is told. Vacating again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The allocation, now vacated.")
    @ApiResponse(responseCode = "404", description = "The allocation does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelAllocationResponse vacate(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long allocationId) {
        return allocations.vacate(AuthenticatedAccount.idFrom(accessToken), allocationId);
    }
}
