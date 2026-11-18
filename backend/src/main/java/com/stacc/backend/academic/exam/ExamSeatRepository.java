package com.stacc.backend.academic.exam;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamSeatRepository extends JpaRepository<ExamSeat, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select seat from ExamSeat seat where seat.examSlot.id = :slotId")
    List<ExamSeat> findForSlotForUpdate(@Param("slotId") Long slotId);

    @Query("select seat.examSlot.id, count(seat) from ExamSeat seat where seat.examSlot.id in :slotIds group by seat.examSlot.id")
    List<Object[]> countForSlots(@Param("slotIds") Collection<Long> slotIds);

    @Query("select seat from ExamSeat seat where seat.courseEnrollment.id in :enrollmentIds")
    List<ExamSeat> findForEnrollments(@Param("enrollmentIds") Collection<Long> enrollmentIds);
}
