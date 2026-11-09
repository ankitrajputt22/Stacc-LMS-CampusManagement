package com.stacc.backend.hostel.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelAllocationService;
import com.stacc.backend.hostel.HostelApplicationRepository;
import com.stacc.backend.hostel.HostelManagementService;
import com.stacc.backend.hostel.HostelOverviewService;
import com.stacc.backend.hostel.HostelOverviewService.BedMap;
import com.stacc.backend.hostel.HostelOverviewService.Occupancy;
import com.stacc.backend.hostel.HostelOverviewService.QueuePlace;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelResponses.HostelAllocationResponse;
import com.stacc.backend.hostel.HostelResponses.HostelBedResponse;
import com.stacc.backend.hostel.HostelResponses.HostelRoomResponse;
import com.stacc.backend.hostel.HostelWardenService;
import com.stacc.backend.hostel.HostelWardenService.WardenView;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hostel structure and daily work: room details, beds under maintenance, wardens, occupancy, the bed map, check-in, and
 * vacating with a condition note (admins with HOSTEL_MANAGE); the warden's own hostels (WARDEN with HOSTEL_WARDEN_VIEW,
 * other hostels the same 404); and a student's place in the queue for a bed.
 */
@RestController
@Tag(name = "Hostel structure and wardens")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HostelStructureController {

    public record RoomDetailsRequest(@Size(max = 50) String block, Integer floorNumber, @Size(max = 50) String roomType,
            @Size(max = 500) String notes) {
    }

    public record MaintenanceRequest(@NotNull Boolean underMaintenance) {
    }

    public record WardenRequest(@NotBlank @Size(max = 100) String loginId, @Size(max = 50) String block) {
    }

    public record NoteRequest(@Size(max = 500) String note) {
    }

    private static final String HOSTEL_UNAVAILABLE = "Hostel not found.";

    private final HostelManagementService management;
    private final HostelWardenService wardens;
    private final HostelOverviewService overview;
    private final HostelAllocationService allocations;
    private final HostelAllocationRepository allocationRows;
    private final HostelApplicationRepository applications;

    public HostelStructureController(HostelManagementService management, HostelWardenService wardens, HostelOverviewService overview,
            HostelAllocationService allocations, HostelAllocationRepository allocationRows, HostelApplicationRepository applications) {
        this.management = management;
        this.wardens = wardens;
        this.overview = overview;
        this.allocations = allocations;
        this.allocationRows = allocationRows;
        this.applications = applications;
    }

    @PutMapping("/api/v1/hostel/admin/rooms/{roomId}/details")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Set a room's block, floor, type, and notes", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public HostelRoomResponse describeRoom(@PathVariable Long roomId, @Valid @RequestBody RoomDetailsRequest request) {
        return management.describeRoom(roomId, request.block(), request.floorNumber(), request.roomType(), request.notes());
    }

    @PutMapping("/api/v1/hostel/admin/beds/{bedId}/maintenance")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Put a free bed under maintenance, or back into use",
            description = "A bed under maintenance takes no allocation. An occupied bed cannot go under maintenance (409).",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public HostelBedResponse maintenance(@PathVariable Long bedId, @Valid @RequestBody MaintenanceRequest request) {
        return management.setMaintenance(bedId, request.underMaintenance());
    }

    @GetMapping("/api/v1/hostel/admin/hostels/{hostelId}/wardens")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "List a hostel's warden assignments", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<WardenView> wardensOf(@PathVariable Long hostelId) {
        return wardens.wardensOf(hostelId);
    }

    @PostMapping("/api/v1/hostel/admin/hostels/{hostelId}/wardens")
    @PreAuthorize(HostelAccess.MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign a warden to a hostel or one of its blocks",
            description = "An active account holding the WARDEN role.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public WardenView assign(@PathVariable Long hostelId, @Valid @RequestBody WardenRequest request) {
        return wardens.assign(hostelId, request.loginId(), request.block());
    }

    @PostMapping("/api/v1/hostel/admin/wardens/{assignmentId}/end")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "End a warden assignment", description = "Kept as history; never deleted.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public WardenView end(@PathVariable Long assignmentId) {
        return wardens.end(assignmentId);
    }

    @GetMapping("/api/v1/hostel/admin/occupancy")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Occupancy of every hostel", description = "Worked out live from beds and current allocations.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<Occupancy> occupancy() {
        return overview.occupancy(null);
    }

    @GetMapping("/api/v1/hostel/admin/hostels/{hostelId}/bed-map")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "A hostel's bed map", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public BedMap adminBedMap(@PathVariable Long hostelId) {
        return overview.bedMap(hostelId);
    }

    @PostMapping("/api/v1/hostel/admin/allocations/{allocationId}/check-in")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Record that the student moved in", description = "Once, with an optional plain-text note.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public HostelAllocationResponse adminCheckIn(@PathVariable Long allocationId, @Valid @RequestBody NoteRequest request) {
        return allocations.checkIn(allocationId, request.note());
    }

    @PostMapping("/api/v1/hostel/admin/allocations/{allocationId}/vacate-with-note")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Vacate an allocation, recording the room's condition", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public HostelAllocationResponse vacateWithNote(@AuthenticationPrincipal Jwt token, @PathVariable Long allocationId,
            @Valid @RequestBody NoteRequest request) {
        return allocations.vacate(AuthenticatedAccount.idFrom(token), allocationId, request.note());
    }

    @GetMapping("/api/v1/hostel/warden/occupancy")
    @PreAuthorize(HostelAccess.WARDEN)
    @Operation(summary = "Occupancy of the hostels you are warden of", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<Occupancy> wardenOccupancy(@AuthenticationPrincipal Jwt token) {
        List<Long> own = wardens.hostelsOf(AuthenticatedAccount.idFrom(token));
        return own.isEmpty() ? List.of() : overview.occupancy(own);
    }

    @GetMapping("/api/v1/hostel/warden/hostels/{hostelId}/bed-map")
    @PreAuthorize(HostelAccess.WARDEN)
    @Operation(summary = "The bed map of a hostel you are warden of", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public BedMap wardenBedMap(@AuthenticationPrincipal Jwt token, @PathVariable Long hostelId) {
        if (!wardens.isWardenOf(AuthenticatedAccount.idFrom(token), hostelId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, HOSTEL_UNAVAILABLE);
        }
        return overview.bedMap(hostelId);
    }

    @PostMapping("/api/v1/hostel/warden/allocations/{allocationId}/check-in")
    @PreAuthorize(HostelAccess.WARDEN)
    @Operation(summary = "Check a student in to a hostel you are warden of", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public HostelAllocationResponse wardenCheckIn(@AuthenticationPrincipal Jwt token, @PathVariable Long allocationId,
            @Valid @RequestBody NoteRequest request) {
        Long hostelId = allocationRows.findWithContextById(allocationId == null ? -1L : allocationId)
                .map(allocation -> allocation.getBed().getRoom().getHostel().getId()).orElse(null);
        if (!wardens.isWardenOf(AuthenticatedAccount.idFrom(token), hostelId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Allocation not found.");
        }
        return allocations.checkIn(allocationId, request.note());
    }

    @GetMapping("/api/v1/hostel/student/applications/{applicationId}/queue")
    @PreAuthorize(HostelAccess.VIEW_OWN)
    @Operation(summary = "Your place in the queue for a bed",
            description = "For an approved application still waiting for a bed: 1 is next. Null position otherwise.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public QueuePlace queue(@AuthenticationPrincipal Jwt token, @PathVariable Long applicationId) {
        Long accountId = AuthenticatedAccount.idFrom(token);
        return applications.findById(applicationId == null ? -1L : applicationId)
                .filter(application -> application.getStudentProfile().getUserAccount().getId().equals(accountId))
                .map(overview::queuePlace)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Hostel application not found."));
    }
}
