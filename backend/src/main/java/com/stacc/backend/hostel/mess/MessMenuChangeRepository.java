package com.stacc.backend.hostel.mess;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessMenuChangeRepository extends JpaRepository<MessMenuChange, Long> {

    @Query("""
            select change from MessMenuChange change where change.hostel.id = :hostelId
            and change.menuDate between :from and :to order by change.menuDate, change.meal
            """)
    List<MessMenuChange> findBetween(@Param("hostelId") Long hostelId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select change from MessMenuChange change where change.hostel.id = :hostelId and change.menuDate = :date and change.meal = :meal")
    Optional<MessMenuChange> findSlot(@Param("hostelId") Long hostelId, @Param("date") LocalDate date, @Param("meal") Meal meal);
}
