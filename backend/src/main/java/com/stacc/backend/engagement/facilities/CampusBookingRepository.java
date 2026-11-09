package com.stacc.backend.engagement.facilities;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusBookingRepository extends JpaRepository<CampusBooking, Long> {

    /** Bookings of the facility not cancelled that overlap the window; read after locking the facility row. */
    @Query("select b from CampusBooking b where b.facility.id = :facilityId and b.cancelledAt is null "
            + "and b.startsAt < :endsAt and b.endsAt > :startsAt order by b.startsAt")
    List<CampusBooking> findOverlapping(@Param("facilityId") Long facilityId, @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt);

    @Query("select b from CampusBooking b join fetch b.facility where b.account.id = :accountId order by b.startsAt desc")
    List<CampusBooking> findAllForAccount(@Param("accountId") Long accountId, Pageable page);

    @Query("select b from CampusBooking b join fetch b.account where b.facility.id = :facilityId and b.cancelledAt is null "
            + "and b.endsAt > :from order by b.startsAt")
    List<CampusBooking> findUpcoming(@Param("facilityId") Long facilityId, @Param("from") Instant from, Pageable page);
}
