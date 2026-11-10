package com.stacc.backend.engagement.groups;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupSessionRepository extends JpaRepository<GroupSession, Long> {

    @Query("select s from GroupSession s join fetch s.group where s.group.id = :groupId and s.endsAt >= :from order by s.startsAt, s.id")
    List<GroupSession> findForGroupFrom(@Param("groupId") Long groupId, @Param("from") Instant from);

    /** Scheduled sessions of these groups that overlap the range, earliest first. */
    @Query("""
            select s from GroupSession s join fetch s.group where s.group.id in :groupIds
            and s.status = com.stacc.backend.engagement.groups.GroupSession.Status.SCHEDULED
            and s.startsAt < :to and s.endsAt > :from order by s.startsAt, s.id
            """)
    List<GroupSession> findScheduledBetween(@Param("groupIds") Collection<Long> groupIds, @Param("from") Instant from, @Param("to") Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GroupSession s join fetch s.group where s.id = :sessionId")
    Optional<GroupSession> findByIdForUpdate(@Param("sessionId") Long sessionId);
}
