package com.stacc.backend.engagement.fests;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestShiftRepository extends JpaRepository<FestShift, Long> {

    @Query("select shift from FestShift shift where shift.fest.id = :festId order by shift.startsAt, shift.id")
    List<FestShift> findForFest(@Param("festId") Long festId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select shift from FestShift shift join fetch shift.fest where shift.id = :shiftId")
    Optional<FestShift> findByIdForUpdate(@Param("shiftId") Long shiftId);
}
