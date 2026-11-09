package com.stacc.backend.hostel;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelRoomRepository extends JpaRepository<HostelRoom, Long> {

    List<HostelRoom> findAllByHostelIdOrderByRoomNumberAscIdAsc(Long hostelId);

    boolean existsByHostelIdAndRoomNumber(Long hostelId, String roomNumber);

    Optional<HostelRoom> findByHostelIdAndRoomNumber(Long hostelId, String roomNumber);

    long countByHostelId(Long hostelId);

    @Query("select room from HostelRoom room join fetch room.hostel where room.id = :roomId")
    Optional<HostelRoom> findWithHostelById(@Param("roomId") Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from HostelRoom room where room.id = :roomId")
    Optional<HostelRoom> findByIdForUpdate(@Param("roomId") Long roomId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select room from HostelRoom room where room.id = :roomId")
    Optional<HostelRoom> findByIdForShare(@Param("roomId") Long roomId);
}
