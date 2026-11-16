package com.stacc.backend.engagement.facilities;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusFacilityClassHourRepository extends JpaRepository<CampusFacilityClassHour, Long> {

    @Query("select h from CampusFacilityClassHour h where h.facility.id = :facilityId and h.removedAt is null order by h.dayOfWeek, h.startsAt, h.id")
    List<CampusFacilityClassHour> findCurrent(@Param("facilityId") Long facilityId);

    @Query("select h from CampusFacilityClassHour h where h.facility.id = :facilityId and h.dayOfWeek = :day and h.removedAt is null")
    List<CampusFacilityClassHour> findCurrentOn(@Param("facilityId") Long facilityId, @Param("day") int day);
}
