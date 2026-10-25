package com.stacc.backend.hostel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What clients may send to the hostel API. No request names a student, profile, requester, approval request, status,
 * application number, or actor: those come from the server and the access token. Codes are not part of updates.
 */
public final class HostelRequests {

    private HostelRequests() {
    }

    public record CreateHostelRequest(
            @Schema(description = "Stored in uppercase: letters, digits, hyphens, and underscores. Unique, and never changed.",
                    example = "MAIN")
            @NotBlank(message = "Code is required")
            @Size(max = Hostel.MAX_CODE_LENGTH, message = "Code must be at most 50 characters")
            String code,

            @Schema(example = "Main Hostel")
            @NotBlank(message = "Name is required")
            @Size(max = Hostel.MAX_NAME_LENGTH, message = "Name must be at most 150 characters")
            String name,

            @Schema(description = "Optional plain text.", nullable = true)
            @Size(max = Hostel.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 2000 characters")
            String description) {
    }

    public record UpdateHostelRequest(
            @NotBlank(message = "Name is required")
            @Size(max = Hostel.MAX_NAME_LENGTH, message = "Name must be at most 150 characters")
            String name,

            @Schema(nullable = true)
            @Size(max = Hostel.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 2000 characters")
            String description,

            @Schema(description = "Whether the hostel takes new allocations. It cannot be made inactive while it has active allocations.")
            @NotNull(message = "Active is required")
            Boolean active) {
    }

    public record CreateRoomRequest(
            @Schema(example = "A-101")
            @NotBlank(message = "Room number is required")
            @Size(max = HostelRoom.MAX_NUMBER_LENGTH, message = "Room number must be at most 50 characters")
            String roomNumber) {
    }

    public record UpdateRoomRequest(
            @Schema(description = "Can change only while no bed of the room was ever allocated.", example = "A-101")
            @NotBlank(message = "Room number is required")
            @Size(max = HostelRoom.MAX_NUMBER_LENGTH, message = "Room number must be at most 50 characters")
            String roomNumber,

            @Schema(description = "Whether the room takes new allocations. It cannot be made inactive while a bed in it is occupied.")
            @NotNull(message = "Active is required")
            Boolean active) {
    }

    public record CreateBedRequest(
            @Schema(example = "B1")
            @NotBlank(message = "Bed number is required")
            @Size(max = HostelBed.MAX_NUMBER_LENGTH, message = "Bed number must be at most 50 characters")
            String bedNumber) {
    }

    public record UpdateBedRequest(
            @Schema(description = "Can change only while the bed was never allocated.", example = "B1")
            @NotBlank(message = "Bed number is required")
            @Size(max = HostelBed.MAX_NUMBER_LENGTH, message = "Bed number must be at most 50 characters")
            String bedNumber,

            @Schema(description = "Whether the bed takes new allocations. An occupied bed cannot be made inactive.")
            @NotNull(message = "Active is required")
            Boolean active) {
    }

    public record CreateHostelApplicationRequest(
            @Schema(description = "One of the sessions the student is eligible for.", example = "2")
            @NotNull(message = "Academic session is required")
            Long academicSessionId,

            @Schema(description = "Optional plain text for the hostel office.", nullable = true)
            @Size(max = HostelApplication.MAX_NOTE_LENGTH, message = "Note must be at most 3000 characters")
            String note) {
    }

    public record CreateHostelAllocationRequest(
            @Schema(description = "An available bed.", example = "7")
            @NotNull(message = "Bed is required")
            Long bedId) {
    }
}
