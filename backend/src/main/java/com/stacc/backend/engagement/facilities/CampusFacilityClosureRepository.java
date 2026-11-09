package com.stacc.backend.engagement.facilities;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusFacilityClosureRepository extends JpaRepository<CampusFacilityClosure, Long> {

    @Query("select c from CampusFacilityClosure c where c.facility.id = :facilityId and c.endDate >= :from order by c.startDate")
    List<CampusFacilityClosure> findFrom(@Param("facilityId") Long facilityId, @Param("from") LocalDate from);

    @Query("select count(c) > 0 from CampusFacilityClosure c where c.facility.id = :facilityId and c.startDate <= :day and c.endDate >= :day")
    boolean closedOn(@Param("facilityId") Long facilityId, @Param("day") LocalDate day);
}
