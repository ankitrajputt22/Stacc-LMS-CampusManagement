package com.stacc.backend.engagement.events;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusEventRegistrationRepository extends JpaRepository<CampusEventRegistration, Long> {

    /** The event's current places and waitlist in order, read with a lock after locking the event row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CampusEventRegistration r where r.event.id = :eventId and r.status in "
            + "(com.stacc.backend.engagement.events.CampusEventRegistration.Status.REGISTERED, "
            + "com.stacc.backend.engagement.events.CampusEventRegistration.Status.WAITLISTED) order by r.registeredAt asc, r.id asc")
    List<CampusEventRegistration> findCurrentForUpdate(@Param("eventId") Long eventId);

    @Query("select r from CampusEventRegistration r join fetch r.account where r.event.id = :eventId and r.status in "
            + "(com.stacc.backend.engagement.events.CampusEventRegistration.Status.REGISTERED, "
            + "com.stacc.backend.engagement.events.CampusEventRegistration.Status.WAITLISTED) order by r.registeredAt asc, r.id asc")
    List<CampusEventRegistration> findCurrent(@Param("eventId") Long eventId);

    @Query("select r from CampusEventRegistration r where r.event.id = :eventId and r.account.id = :accountId and r.status in "
            + "(com.stacc.backend.engagement.events.CampusEventRegistration.Status.REGISTERED, "
            + "com.stacc.backend.engagement.events.CampusEventRegistration.Status.WAITLISTED)")
    Optional<CampusEventRegistration> findCurrentForAccount(@Param("eventId") Long eventId, @Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CampusEventRegistration r join fetch r.account join fetch r.event where r.id = :registrationId")
    Optional<CampusEventRegistration> findByIdForUpdate(@Param("registrationId") Long registrationId);
}
