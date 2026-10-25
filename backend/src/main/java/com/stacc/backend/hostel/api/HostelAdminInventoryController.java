package com.stacc.backend.hostel.api;

import java.util.List;

import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.stacc.backend.hostel.HostelManagementService;
import com.stacc.backend.hostel.HostelRequests.CreateBedRequest;
import com.stacc.backend.hostel.HostelRequests.CreateHostelRequest;
import com.stacc.backend.hostel.HostelRequests.CreateRoomRequest;
import com.stacc.backend.hostel.HostelRequests.UpdateBedRequest;
import com.stacc.backend.hostel.HostelRequests.UpdateHostelRequest;
import com.stacc.backend.hostel.HostelRequests.UpdateRoomRequest;
import com.stacc.backend.hostel.HostelResponses.HostelBedResponse;
import com.stacc.backend.hostel.HostelResponses.HostelResponse;
import com.stacc.backend.hostel.HostelResponses.HostelRoomResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The hostel inventory for the hostel office: hostels, rooms, and beds. Every route needs the admin role and
 * HOSTEL_MANAGE. Nothing is deleted: anything can be made inactive, but not while it holds an active allocation.
 * Occupancy and capacity are counted from allocations and beds, never stored.
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
public class HostelAdminInventoryController {

    private final HostelManagementService management;

    public HostelAdminInventoryController(HostelManagementService management) {
        this.management = management;
    }

    @GetMapping("/hostels")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "List hostels", description = "Every hostel, active and inactive, by code, with room, active-bed, and occupied-bed counts.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The hostels. The list may be empty.")
    public List<HostelResponse> hostels() {
        return management.hostels();
    }

    @GetMapping("/hostels/{hostelId}")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Get one hostel", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The hostel.")
    @ApiResponse(responseCode = "404", description = "The hostel does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelResponse hostel(@PathVariable Long hostelId) {
        return management.hostel(hostelId);
    }

    @PostMapping("/hostels")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Create a hostel", description = "Adds an active hostel. The code is stored in uppercase and never changes.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new hostel.")
    @ApiResponse(responseCode = "400", description = "A field is missing or too long, or the code has other characters.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "A hostel with this code already exists.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelResponse createHostel(@Valid @RequestBody CreateHostelRequest request) {
        return management.createHostel(request);
    }

    @PutMapping("/hostels/{hostelId}")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Update a hostel", description = "Replaces the name, description, and active flag. The code is not part of the request.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The updated hostel.")
    @ApiResponse(responseCode = "400", description = "A field is missing or too long.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The hostel does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The hostel cannot be made inactive while it has active allocations.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelResponse updateHostel(@PathVariable Long hostelId, @Valid @RequestBody UpdateHostelRequest request) {
        return management.updateHostel(hostelId, request);
    }

    @GetMapping("/hostels/{hostelId}/rooms")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "List a hostel's rooms", description = "Every room of the hostel, by number, with its capacity (active beds) and occupied beds.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The rooms. The list may be empty.")
    @ApiResponse(responseCode = "404", description = "The hostel does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<HostelRoomResponse> rooms(@PathVariable Long hostelId) {
        return management.rooms(hostelId);
    }

    @PostMapping("/hostels/{hostelId}/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Add a room", description = "Adds an active room. Its number is unique within the hostel.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new room.")
    @ApiResponse(responseCode = "400", description = "The room number is missing or too long.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The hostel does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The hostel already has a room with this number.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelRoomResponse createRoom(@PathVariable Long hostelId, @Valid @RequestBody CreateRoomRequest request) {
        return management.createRoom(hostelId, request);
    }

    @PutMapping("/rooms/{roomId}")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Update a room", description = "Changes the room number while no bed of the room was ever allocated, and the active flag.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The updated room.")
    @ApiResponse(responseCode = "400", description = "A field is missing or too long.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The room does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The number is taken or fixed by allocation history, or a bed in the room is occupied.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelRoomResponse updateRoom(@PathVariable Long roomId, @Valid @RequestBody UpdateRoomRequest request) {
        return management.updateRoom(roomId, request);
    }

    @GetMapping("/rooms/{roomId}/beds")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "List a room's beds", description = "Every bed of the room, by number, with its derived occupancy: AVAILABLE, OCCUPIED, or INACTIVE.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The beds. The list may be empty.")
    @ApiResponse(responseCode = "404", description = "The room does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<HostelBedResponse> beds(@PathVariable Long roomId) {
        return management.beds(roomId);
    }

    @PostMapping("/rooms/{roomId}/beds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Add a bed", description = "Adds an active bed. Its number is unique within the room.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new bed.")
    @ApiResponse(responseCode = "400", description = "The bed number is missing or too long.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The room does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The room already has a bed with this number.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelBedResponse createBed(@PathVariable Long roomId, @Valid @RequestBody CreateBedRequest request) {
        return management.createBed(roomId, request);
    }

    @PutMapping("/beds/{bedId}")
    @PreAuthorize(HostelAccess.MANAGE)
    @Operation(summary = "Update a bed", description = "Changes the bed number while the bed was never allocated, and the active flag.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The updated bed.")
    @ApiResponse(responseCode = "400", description = "A field is missing or too long.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The bed does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The number is taken or fixed by allocation history, or the bed is occupied.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelBedResponse updateBed(@PathVariable Long bedId, @Valid @RequestBody UpdateBedRequest request) {
        return management.updateBed(bedId, request);
    }
}
