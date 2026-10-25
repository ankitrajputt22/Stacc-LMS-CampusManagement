package com.stacc.backend.hostel;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelBedRepository extends JpaRepository<HostelBed, Long> {

    List<HostelBed> findAllByRoomIdOrderByBedNumberAscIdAsc(Long roomId);

    boolean existsByRoomIdAndBedNumber(Long roomId, String bedNumber);

    @Query("select bed from HostelBed bed join fetch bed.room room join fetch room.hostel where bed.id = :bedId")
    Optional<HostelBed> findWithContextById(@Param("bedId") Long bedId);

    /** Where a bed is, as its room and hostel IDs, so they can be locked in order before the bed. */
    @Query("select bed.room.id as roomId, bed.room.hostel.id as hostelId from HostelBed bed where bed.id = :bedId")
    Optional<BedLocation> findLocationById(@Param("bedId") Long bedId);

    interface BedLocation {

        Long getRoomId();

        Long getHostelId();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bed from HostelBed bed where bed.id = :bedId")
    Optional<HostelBed> findByIdForUpdate(@Param("bedId") Long bedId);

    /** How many active beds each room of a hostel has: a room's capacity. */
    @Query("""
            select bed.room.id as roomId, count(bed) as total
            from HostelBed bed
            where bed.room.hostel.id = :hostelId and bed.active = true
            group by bed.room.id
            """)
    List<RoomCount> countActiveBedsByRoom(@Param("hostelId") Long hostelId);

    @Query("select count(bed) from HostelBed bed where bed.room.hostel.id = :hostelId and bed.active = true")
    long countActiveInHostel(@Param("hostelId") Long hostelId);

    interface RoomCount {

        Long getRoomId();

        Long getTotal();
    }

    /**
     * The beds a new allocation can use: active beds in active rooms of active hostels with no ACTIVE allocation,
     * narrowed to one hostel or room when given. By hostel code, room number, and bed number.
     */
    @Query("""
            select bed
            from HostelBed bed
            join fetch bed.room room
            join fetch room.hostel hostel
            where bed.active = true and room.active = true and hostel.active = true
              and (:hostelId is null or hostel.id = :hostelId)
              and (:roomId is null or room.id = :roomId)
              and not exists (
                  select allocation.id from HostelAllocation allocation
                  where allocation.bed = bed
                    and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE)
            order by hostel.code asc, room.roomNumber asc, bed.bedNumber asc, bed.id asc
            """)
    List<HostelBed> findAvailable(@Param("hostelId") Long hostelId, @Param("roomId") Long roomId);
}
