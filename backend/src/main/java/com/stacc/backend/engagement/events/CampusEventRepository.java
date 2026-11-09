package com.stacc.backend.engagement.events;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusEventRepository extends JpaRepository<CampusEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CampusEvent e where e.id = :id")
    Optional<CampusEvent> findByIdForUpdate(@Param("id") Long id);

    /** Events that may be visible, ending after the moment, soonest first (the effective status is worked out on read). */
    @Query("select e from CampusEvent e left join fetch e.group where e.status in "
            + "(com.stacc.backend.engagement.events.CampusEvent.Status.PUBLISHED, com.stacc.backend.engagement.events.CampusEvent.Status.PENDING_APPROVAL, "
            + "com.stacc.backend.engagement.events.CampusEvent.Status.CANCELLED) and e.endsAt > :after order by e.startsAt asc")
    List<CampusEvent> findShowable(@Param("after") Instant after);

    /** Events the account organizes, or of groups it is an officer of, newest first. */
    @Query("select distinct e from CampusEvent e left join fetch e.group g where e.organizer.id = :accountId or g.id in "
            + "(select o.group.id from CampusGroupOfficer o where o.account.id = :accountId and o.endedAt is null) order by e.startsAt desc")
    List<CampusEvent> findManagedBy(@Param("accountId") Long accountId);

    @Query("select e from CampusEvent e left join fetch e.group order by e.startsAt desc")
    List<CampusEvent> findAllForAdmin();
}
