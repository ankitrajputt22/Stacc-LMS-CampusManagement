package com.stacc.backend.academic.exam;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamSlotRepository extends JpaRepository<ExamSlot, Long> {

    @Query("""
            select slot from ExamSlot slot join fetch slot.courseOffering offering join fetch offering.course
            where slot.examSession.id in :sessionIds and slot.removedAt is null
            order by slot.examDate, slot.startsAt, slot.id
            """)
    List<ExamSlot> findLiveForSessions(@Param("sessionIds") Collection<Long> sessionIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from ExamSlot slot join fetch slot.courseOffering where slot.id = :id")
    Optional<ExamSlot> findByIdForUpdate(@Param("id") Long id);
}
