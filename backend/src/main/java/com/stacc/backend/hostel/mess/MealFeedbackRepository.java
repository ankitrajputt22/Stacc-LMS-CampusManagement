package com.stacc.backend.hostel.mess;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealFeedbackRepository extends JpaRepository<MealFeedback, Long> {

    @Query("""
            select feedback from MealFeedback feedback where feedback.studentAccountId = :accountId
            and feedback.mealDate between :from and :to
            """)
    List<MealFeedback> findOwnBetween(@Param("accountId") Long accountId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** A hostel's feedback in a period, newest first, without who gave it. */
    @Query("""
            select feedback from MealFeedback feedback where feedback.hostel.id = :hostelId
            and feedback.mealDate between :from and :to order by feedback.mealDate desc, feedback.meal, feedback.id desc
            """)
    List<MealFeedback> findForHostelBetween(@Param("hostelId") Long hostelId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
