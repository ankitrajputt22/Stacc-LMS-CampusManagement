package com.stacc.backend.fees;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeePaymentRepository extends JpaRepository<FeePayment, Long> {

    @Query("select p from FeePayment p where p.student.id = :studentId and p.academicSession.id = :sessionId order by p.paidOn, p.id")
    List<FeePayment> findForStudent(@Param("studentId") Long studentId, @Param("sessionId") Long sessionId);
}
