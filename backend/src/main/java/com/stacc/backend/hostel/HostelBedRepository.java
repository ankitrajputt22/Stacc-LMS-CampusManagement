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

    @Query("select bed.room.id as roomId, bed.room.hostel.id as hostelId from HostelBed bed where bed.id = :bedId")
    Optional<BedLocation> findLocationById(@Param("bedId") Long bedId);

    interface BedLocation {

        Long getRoomId();

        Long getHostelId();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bed from HostelBed bed where bed.id = :bedId")
    Optional<HostelBed> findByIdForUpdate(@Param("bedId") Long bedId);

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

    @Query("""
            select bed
            from HostelBed bed
            join fetch bed.room room
            join fetch room.hostel hostel
            where bed.active = true and bed.underMaintenance = false and room.active = true and hostel.active = true
              and (:hostelId is null or hostel.id = :hostelId)
              and (:roomId is null or room.id = :roomId)
              and not exists (
                  select allocation.id from HostelAllocation allocation
                  where allocation.bed = bed
                    and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE)
            order by hostel.code asc, room.roomNumber asc, bed.bedNumber asc, bed.id asc
            """)
    List<HostelBed> findAvailable(@Param("hostelId") Long hostelId, @Param("roomId") Long roomId);

    @Query("""
            select bed from HostelBed bed join fetch bed.room room
            where room.hostel.id = :hostelId
            order by room.block asc, room.floorNumber asc, room.roomNumber asc, bed.bedNumber asc, bed.id asc
            """)
    List<HostelBed> findAllInHostel(@Param("hostelId") Long hostelId);

    interface HostelBedCount {
        Long getHostelId();
        long getBeds();
        long getMaintenance();
    }

    @Query("""
            select bed.room.hostel.id as hostelId, count(bed) as beds,
                   sum(case when bed.underMaintenance = true then 1 else 0 end) as maintenance
            from HostelBed bed where bed.active = true and bed.room.active = true group by bed.room.hostel.id
            """)
    List<HostelBedCount> countBedsByHostel();
}
