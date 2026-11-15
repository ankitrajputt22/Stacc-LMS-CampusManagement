package com.stacc.backend.lms.ai.control;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLogEntry, Long> {

    /** Rows since a moment as (outcome, count, average duration in milliseconds). */
    @Query("select cast(entry.outcome as string), count(entry), avg(entry.durationMs) from AiRequestLogEntry entry"
            + " where entry.createdAt >= :since group by entry.outcome")
    List<Object[]> countByOutcomeSince(@Param("since") Instant since);

    @Query("select cast(entry.kind as string), count(entry) from AiRequestLogEntry entry"
            + " where entry.createdAt >= :since group by entry.kind")
    List<Object[]> countByKindSince(@Param("since") Instant since);

    @Transactional
    @Modifying
    @Query("delete from AiRequestLogEntry entry where entry.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
