package com.stacc.backend.calendar.personal;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarFeedLinkRepository extends JpaRepository<CalendarFeedLink, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CalendarFeedLink l where l.userAccountId = :accountId and l.revokedAt is null")
    Optional<CalendarFeedLink> findActiveForUpdate(@Param("accountId") Long accountId);

    @Query("select l from CalendarFeedLink l where l.userAccountId = :accountId and l.revokedAt is null")
    Optional<CalendarFeedLink> findActive(@Param("accountId") Long accountId);

    @Query("select l from CalendarFeedLink l where l.tokenFingerprint = :fingerprint and l.revokedAt is null")
    Optional<CalendarFeedLink> findActiveByFingerprint(@Param("fingerprint") String fingerprint);
}
