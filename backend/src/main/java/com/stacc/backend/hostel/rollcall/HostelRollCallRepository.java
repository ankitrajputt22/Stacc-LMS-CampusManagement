package com.stacc.backend.hostel.rollcall;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelRollCallRepository extends JpaRepository<HostelRollCall, Long> {

    @Query("select rollCall from HostelRollCall rollCall where rollCall.hostel.id = :hostelId order by rollCall.rollDate desc, rollCall.id desc")
    List<HostelRollCall> findRecentForHostel(@Param("hostelId") Long hostelId, Pageable page);

    @Query("select rollCall.hostel.id from HostelRollCall rollCall where rollCall.id = :rollCallId")
    Optional<Long> findHostelIdById(@Param("rollCallId") Long rollCallId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rollCall from HostelRollCall rollCall join fetch rollCall.hostel where rollCall.id = :rollCallId")
    Optional<HostelRollCall> findByIdForUpdate(@Param("rollCallId") Long rollCallId);

    @Query("select rollCall from HostelRollCall rollCall join fetch rollCall.hostel where rollCall.id = :rollCallId")
    Optional<HostelRollCall> findWithHostelById(@Param("rollCallId") Long rollCallId);
}
