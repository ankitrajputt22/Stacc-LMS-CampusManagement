package com.stacc.backend.hostel;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelRepository extends JpaRepository<Hostel, Long> {

    boolean existsByCode(String code);

    Optional<Hostel> findByCode(String code);

    List<Hostel> findAllByOrderByCodeAscIdAsc();

    /** Locked for writing: deactivating a hostel takes this lock, so no allocation into it can finish at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hostel from Hostel hostel where hostel.id = :hostelId")
    Optional<Hostel> findByIdForUpdate(@Param("hostelId") Long hostelId);

    /** Locked for reading ({@code FOR SHARE}): allocations take this lock, so they wait for a deactivation, not each other. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select hostel from Hostel hostel where hostel.id = :hostelId")
    Optional<Hostel> findByIdForShare(@Param("hostelId") Long hostelId);
}
