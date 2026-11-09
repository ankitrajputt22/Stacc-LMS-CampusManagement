package com.stacc.backend.hostel;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelWardenRepository extends JpaRepository<HostelWarden, Long> {

    @Query("select w from HostelWarden w join fetch w.warden where w.hostel.id = :hostelId order by w.endedAt asc, w.assignedAt desc")
    List<HostelWarden> findAllByHostel(@Param("hostelId") Long hostelId);

    /** The hostels the account is an active warden of. */
    @Query("select distinct w.hostel.id from HostelWarden w where w.warden.id = :accountId and w.endedAt is null")
    List<Long> findActiveHostelIds(@Param("accountId") Long accountId);

    /** The active wardens of a hostel, for a block first, then for the whole hostel. */
    @Query("select w from HostelWarden w join fetch w.warden where w.hostel.id = :hostelId and w.endedAt is null "
            + "order by case when w.block is null then 1 else 0 end, w.assignedAt asc")
    List<HostelWarden> findActiveByHostel(@Param("hostelId") Long hostelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from HostelWarden w where w.id = :id")
    Optional<HostelWarden> findByIdForUpdate(@Param("id") Long id);
}
