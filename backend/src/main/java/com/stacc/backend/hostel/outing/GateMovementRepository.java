package com.stacc.backend.hostel.outing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GateMovementRepository extends JpaRepository<GateMovement, Long> {

    /** The student's latest movement: OUT means outside now. */
    Optional<GateMovement> findFirstByStudentAccountIdOrderByRecordedAtDescIdDesc(Long studentAccountId);

    @Query("select m from GateMovement m order by m.recordedAt desc, m.id desc")
    List<GateMovement> findRecent(Pageable page);
}
