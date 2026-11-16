package com.stacc.backend.fees;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeeAdjustmentRepository extends JpaRepository<FeeAdjustment, Long> {

    @Query("select a from FeeAdjustment a where a.student.id = :studentId and a.academicSession.id = :sessionId order by a.createdAt desc, a.id desc")
    List<FeeAdjustment> findForStudent(@Param("studentId") Long studentId, @Param("sessionId") Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from FeeAdjustment a where a.id = :id")
    Optional<FeeAdjustment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select a from FeeAdjustment a join fetch a.student join fetch a.academicSession
            where (:status is null or a.status = :status)
            order by case when a.status = com.stacc.backend.fees.FeeAdjustment.Status.SUBMITTED then 0 else 1 end, a.createdAt desc
            """)
    List<FeeAdjustment> queue(@Param("status") FeeAdjustment.Status status, Pageable page);
}
