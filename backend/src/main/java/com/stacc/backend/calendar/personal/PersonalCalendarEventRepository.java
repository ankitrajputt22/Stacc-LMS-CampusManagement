package com.stacc.backend.calendar.personal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalCalendarEventRepository extends JpaRepository<PersonalCalendarEvent, Long> {

    /** The account's own events that overlap the range, earliest first. */
    @Query("select e from PersonalCalendarEvent e where e.userAccountId = :accountId and e.startDate <= :to and e.endDate >= :from "
            + "order by e.startDate asc, e.id asc")
    List<PersonalCalendarEvent> findOverlapping(@Param("accountId") Long accountId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    Optional<PersonalCalendarEvent> findByIdAndUserAccountId(Long id, Long userAccountId);
}
