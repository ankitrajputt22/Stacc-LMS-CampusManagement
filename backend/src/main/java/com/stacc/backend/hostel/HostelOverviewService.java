package com.stacc.backend.hostel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only views worked out live from beds and ACTIVE allocations: occupancy per hostel, a hostel's bed map, and a
 * student's place in the queue for a bed. Nothing here is stored. Who may ask is checked by the caller.
 */
@Service
@Transactional(readOnly = true)
public class HostelOverviewService {

    public record Occupancy(Long hostelId, String code, String name, boolean active, long beds, long occupied,
            long maintenance, long available) {
    }

    public record BedCell(Long bedId, String bedNumber, String state, String studentLoginId, Long allocationId, boolean checkedIn) {
    }

    public record RoomRow(Long roomId, String roomNumber, String block, Integer floorNumber, String roomType, boolean active,
            List<BedCell> beds) {
    }

    public record BedMap(Long hostelId, String code, String name, List<RoomRow> rooms) {
    }

    /** A waiting student's place: 1 is next. Null position when the application is not waiting for a bed. */
    public record QueuePlace(Integer position, int waiting) {
    }

    private final HostelRepository hostels;
    private final HostelBedRepository beds;
    private final HostelAllocationRepository allocations;
    private final HostelApplicationRepository applications;

    public HostelOverviewService(HostelRepository hostels, HostelBedRepository beds, HostelAllocationRepository allocations,
            HostelApplicationRepository applications) {
        this.hostels = hostels;
        this.beds = beds;
        this.allocations = allocations;
        this.applications = applications;
    }

    /** Occupancy of the given hostels, or of all with null. Counts active beds in active rooms only. */
    public List<Occupancy> occupancy(List<Long> hostelIds) {
        Map<Long, HostelBedRepository.HostelBedCount> bedCounts = beds.countBedsByHostel().stream()
                .collect(Collectors.toMap(HostelBedRepository.HostelBedCount::getHostelId, Function.identity()));
        Map<Long, Long> occupied = allocations.countOccupiedByHostel().stream()
                .collect(Collectors.toMap(HostelAllocationRepository.HostelCount::getHostelId, HostelAllocationRepository.HostelCount::getCount));
        return hostels.findAll().stream()
                .filter(hostel -> hostelIds == null || hostelIds.contains(hostel.getId()))
                .sorted(java.util.Comparator.comparing(Hostel::getCode))
                .map(hostel -> {
                    HostelBedRepository.HostelBedCount count = bedCounts.get(hostel.getId());
                    long total = count == null ? 0 : count.getBeds();
                    long maintenance = count == null ? 0 : count.getMaintenance();
                    long taken = occupied.getOrDefault(hostel.getId(), 0L);
                    return new Occupancy(hostel.getId(), hostel.getCode(), hostel.getName(), hostel.isActive(), total, taken,
                            maintenance, Math.max(0, total - taken - maintenance));
                })
                .toList();
    }

    /** Every room and bed of a hostel with its state and, while occupied, the student's login ID. */
    public BedMap bedMap(Long hostelId) {
        Hostel hostel = hostels.findById(hostelId == null ? -1L : hostelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Hostel not found."));
        Map<Long, HostelAllocation> byBed = allocations.findActiveInHostel(hostelId).stream()
                .collect(Collectors.toMap(allocation -> allocation.getBed().getId(), Function.identity(), (a, b) -> a));
        Map<HostelRoom, List<HostelBed>> byRoom = beds.findAllInHostel(hostelId).stream()
                .collect(Collectors.groupingBy(HostelBed::getRoom, java.util.LinkedHashMap::new, Collectors.toList()));
        List<RoomRow> rooms = byRoom.entrySet().stream().map(entry -> {
            HostelRoom room = entry.getKey();
            List<BedCell> cells = entry.getValue().stream().map(bed -> {
                HostelAllocation allocation = byBed.get(bed.getId());
                String state = allocation != null ? "OCCUPIED"
                        : bed.isUnderMaintenance() ? "MAINTENANCE"
                        : bed.isActive() && room.isActive() && hostel.isActive() ? "AVAILABLE" : "INACTIVE";
                return new BedCell(bed.getId(), bed.getBedNumber(), state,
                        allocation == null ? null : allocation.getHostelApplication().getStudentProfile().getUserAccount().getLoginId(),
                        allocation == null ? null : allocation.getId(), allocation != null && allocation.getCheckedInAt() != null);
            }).toList();
            return new RoomRow(room.getId(), room.getRoomNumber(), room.getBlock(), room.getFloorNumber(), room.getRoomType(),
                    room.isActive(), cells);
        }).toList();
        return new BedMap(hostel.getId(), hostel.getCode(), hostel.getName(), rooms);
    }

    /** Where an approved application waiting for a bed stands among those of its session. */
    public QueuePlace queuePlace(HostelApplication application) {
        List<Long> waiting = applications.findWaitingForBed(application.getAcademicSession().getId());
        int index = waiting.indexOf(application.getId());
        return new QueuePlace(index < 0 ? null : index + 1, waiting.size());
    }
}
