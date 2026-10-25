package com.stacc.backend.hostel;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelRequests.CreateBedRequest;
import com.stacc.backend.hostel.HostelRequests.CreateHostelRequest;
import com.stacc.backend.hostel.HostelRequests.CreateRoomRequest;
import com.stacc.backend.hostel.HostelRequests.UpdateBedRequest;
import com.stacc.backend.hostel.HostelRequests.UpdateHostelRequest;
import com.stacc.backend.hostel.HostelRequests.UpdateRoomRequest;
import com.stacc.backend.hostel.HostelResponses.HostelBedResponse;
import com.stacc.backend.hostel.HostelResponses.HostelResponse;
import com.stacc.backend.hostel.HostelResponses.HostelRoomResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The hostel inventory: hostels, their rooms, and their beds, for admins. Nothing is deleted; anything can be made
 * inactive, but not while it holds an active allocation. A hostel's code never changes, and a room's or bed's number
 * stops changing once it has allocation history. Occupancy and capacity are always counted, never stored.
 */
@Service
@Transactional
public class HostelManagementService {

    static final String HOSTEL_NOT_FOUND = "Hostel not found.";
    static final String ROOM_NOT_FOUND = "Room not found.";
    static final String BED_NOT_FOUND = "Bed not found.";
    static final String CODE_TAKEN = "A hostel with this code already exists.";
    static final String ROOM_TAKEN = "This hostel already has a room with this number.";
    static final String BED_TAKEN = "This room already has a bed with this number.";

    private final HostelRepository hostels;
    private final HostelRoomRepository rooms;
    private final HostelBedRepository beds;
    private final HostelAllocationRepository allocations;

    public HostelManagementService(HostelRepository hostels, HostelRoomRepository rooms, HostelBedRepository beds,
            HostelAllocationRepository allocations) {
        this.hostels = hostels;
        this.rooms = rooms;
        this.beds = beds;
        this.allocations = allocations;
    }

    // --- Hostels

    @Transactional(readOnly = true)
    public List<HostelResponse> hostels() {
        return hostels.findAllByOrderByCodeAscIdAsc().stream().map(this::responseOf).toList();
    }

    @Transactional(readOnly = true)
    public HostelResponse hostel(Long hostelId) {
        return responseOf(hostelOf(hostelId));
    }

    public HostelResponse createHostel(CreateHostelRequest request) {
        String code;
        try {
            code = Hostel.normalizedCode(request.code());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The code must be letters, digits, hyphens, and underscores.");
        }
        if (hostels.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, CODE_TAKEN);
        }
        Hostel hostel = valid(() -> new Hostel(code, request.name(), request.description()));
        return responseOf(saved(() -> hostels.saveAndFlush(hostel), CODE_TAKEN));
    }

    /** Replaces the name, description, and active flag. The code never changes. */
    public HostelResponse updateHostel(Long hostelId, UpdateHostelRequest request) {
        Hostel hostel = (hostelId == null ? null : hostels.findByIdForUpdate(hostelId).orElse(null));
        if (hostel == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, HOSTEL_NOT_FOUND);
        }
        if (hostel.isActive() && !request.active() && allocations.existsActiveInHostel(hostelId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This hostel cannot be made inactive while it has active allocations. Vacate them first.");
        }
        valid(() -> {
            hostel.update(request.name(), request.description(), request.active());
            return hostel;
        });
        return responseOf(hostels.saveAndFlush(hostel));
    }

    // --- Rooms

    @Transactional(readOnly = true)
    public List<HostelRoomResponse> rooms(Long hostelId) {
        hostelOf(hostelId);
        Map<Long, Long> activeBeds = counts(beds.countActiveBedsByRoom(hostelId));
        Map<Long, Long> occupied = counts(allocations.countOccupiedByRoom(hostelId));
        Set<Long> locked = new HashSet<>(allocations.findAllocatedRoomIdsInHostel(hostelId));
        return rooms.findAllByHostelIdOrderByRoomNumberAscIdAsc(hostelId).stream()
                .map(room -> new HostelRoomResponse(room.getId(), hostelId, room.getRoomNumber(), room.isActive(),
                        activeBeds.getOrDefault(room.getId(), 0L), occupied.getOrDefault(room.getId(), 0L),
                        locked.contains(room.getId())))
                .toList();
    }

    public HostelRoomResponse createRoom(Long hostelId, CreateRoomRequest request) {
        Hostel hostel = hostelOf(hostelId);
        String number = trimmed(request.roomNumber());
        if (rooms.existsByHostelIdAndRoomNumber(hostelId, number)) {
            throw new ApiException(HttpStatus.CONFLICT, ROOM_TAKEN);
        }
        HostelRoom room = valid(() -> new HostelRoom(hostel, number));
        HostelRoom stored = saved(() -> rooms.saveAndFlush(room), ROOM_TAKEN);
        return new HostelRoomResponse(stored.getId(), hostelId, stored.getRoomNumber(), stored.isActive(), 0, 0, false);
    }

    /** Changes the room number, while no bed of the room was ever allocated, and the active flag. */
    public HostelRoomResponse updateRoom(Long roomId, UpdateRoomRequest request) {
        HostelRoom room = (roomId == null ? null : rooms.findByIdForUpdate(roomId).orElse(null));
        if (room == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ROOM_NOT_FOUND);
        }
        Long hostelId = room.getHostel().getId();
        String number = trimmed(request.roomNumber());
        boolean renumbered = !number.equals(room.getRoomNumber());
        if (renumbered && allocations.existsAnyInRoom(roomId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This room's number can no longer change: the room has allocation history.");
        }
        if (renumbered && rooms.existsByHostelIdAndRoomNumber(hostelId, number)) {
            throw new ApiException(HttpStatus.CONFLICT, ROOM_TAKEN);
        }
        if (room.isActive() && !request.active() && allocations.existsActiveInRoom(roomId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This room cannot be made inactive while a bed in it is occupied. Vacate it first.");
        }
        valid(() -> {
            room.update(number, request.active());
            return room;
        });
        saved(() -> rooms.saveAndFlush(room), ROOM_TAKEN);
        return rooms(hostelId).stream().filter(row -> row.roomId().equals(roomId)).findFirst().orElseThrow();
    }

    // --- Beds

    @Transactional(readOnly = true)
    public List<HostelBedResponse> beds(Long roomId) {
        HostelRoom room = roomOf(roomId);
        Set<Long> occupied = new HashSet<>(allocations.findOccupiedBedIdsInRoom(roomId));
        Set<Long> locked = new HashSet<>(allocations.findAllocatedBedIdsInRoom(roomId));
        boolean roomUsable = room.isActive() && room.getHostel().isActive();
        return beds.findAllByRoomIdOrderByBedNumberAscIdAsc(roomId).stream()
                .map(bed -> new HostelBedResponse(bed.getId(), roomId, bed.getBedNumber(), bed.isActive(),
                        occupied.contains(bed.getId()) ? "OCCUPIED" : bed.isActive() && roomUsable ? "AVAILABLE" : "INACTIVE",
                        locked.contains(bed.getId())))
                .toList();
    }

    public HostelBedResponse createBed(Long roomId, CreateBedRequest request) {
        HostelRoom room = roomOf(roomId);
        String number = trimmed(request.bedNumber());
        if (beds.existsByRoomIdAndBedNumber(roomId, number)) {
            throw new ApiException(HttpStatus.CONFLICT, BED_TAKEN);
        }
        HostelBed bed = valid(() -> new HostelBed(room, number));
        HostelBed stored = saved(() -> beds.saveAndFlush(bed), BED_TAKEN);
        return beds(roomId).stream().filter(row -> row.bedId().equals(stored.getId())).findFirst().orElseThrow();
    }

    /** Changes the bed number, while the bed was never allocated, and the active flag. */
    public HostelBedResponse updateBed(Long bedId, UpdateBedRequest request) {
        HostelBed bed = (bedId == null ? null : beds.findByIdForUpdate(bedId).orElse(null));
        if (bed == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, BED_NOT_FOUND);
        }
        Long roomId = bed.getRoom().getId();
        String number = trimmed(request.bedNumber());
        boolean renumbered = !number.equals(bed.getBedNumber());
        if (renumbered && allocations.existsAnyForBed(bedId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This bed's number can no longer change: the bed has allocation history.");
        }
        if (renumbered && beds.existsByRoomIdAndBedNumber(roomId, number)) {
            throw new ApiException(HttpStatus.CONFLICT, BED_TAKEN);
        }
        if (bed.isActive() && !request.active() && allocations.existsActiveForBed(bedId)) {
            throw new ApiException(HttpStatus.CONFLICT, "An occupied bed cannot be made inactive. Vacate it first.");
        }
        valid(() -> {
            bed.update(number, request.active());
            return bed;
        });
        saved(() -> beds.saveAndFlush(bed), BED_TAKEN);
        return beds(roomId).stream().filter(row -> row.bedId().equals(bedId)).findFirst().orElseThrow();
    }

    // ---

    private HostelResponse responseOf(Hostel hostel) {
        return new HostelResponse(hostel.getId(), hostel.getCode(), hostel.getName(), hostel.getDescription(),
                hostel.isActive(), rooms.countByHostelId(hostel.getId()), beds.countActiveInHostel(hostel.getId()),
                allocations.countActiveInHostel(hostel.getId()), hostel.getCreatedAt(), hostel.getUpdatedAt());
    }

    private Hostel hostelOf(Long hostelId) {
        return (hostelId == null ? Optional.<Hostel>empty() : hostels.findById(hostelId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, HOSTEL_NOT_FOUND));
    }

    private HostelRoom roomOf(Long roomId) {
        return (roomId == null ? Optional.<HostelRoom>empty() : rooms.findWithHostelById(roomId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ROOM_NOT_FOUND));
    }

    private static Map<Long, Long> counts(List<HostelBedRepository.RoomCount> rows) {
        return rows.stream().collect(Collectors.toMap(HostelBedRepository.RoomCount::getRoomId,
                HostelBedRepository.RoomCount::getTotal));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.strip();
    }

    private static <T> T valid(Supplier<T> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The request is not valid: " + invalid.getMessage() + ".");
        }
    }

    // A unique constraint caught a race the check above could not see.
    private static <T> T saved(Supplier<T> save, String conflict) {
        try {
            return save.get();
        } catch (DataIntegrityViolationException clash) {
            throw new ApiException(HttpStatus.CONFLICT, conflict);
        }
    }
}
