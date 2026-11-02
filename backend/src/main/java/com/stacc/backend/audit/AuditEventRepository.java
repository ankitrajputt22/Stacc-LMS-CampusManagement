package com.stacc.backend.audit;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Append-only: it can save and read, and has no update or delete of any kind. */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    AuditEvent save(AuditEvent event);

    /**
     * One page of events, newest first, matching every filter given. The pattern (lower-cased, escaped with
     * {@code !}) matches the login ID of the actor or of a target account.
     */
    @Query("""
            select event from AuditEvent event
            where (:action is null or event.action = :action)
              and (:from is null or event.occurredAt >= :from)
              and (:to is null or event.occurredAt < :to)
              and (:pattern is null or exists (
                    select account.id from UserAccount account
                    where (account.id = event.actorAccountId
                           or (event.targetType = 'USER_ACCOUNT' and account.id = event.targetId))
                      and lower(account.loginId) like :pattern escape '!'))
            order by event.occurredAt desc, event.id desc
            """)
    Page<AuditEvent> search(@Param("action") AuditAction action, @Param("from") Instant from, @Param("to") Instant to,
            @Param("pattern") String pattern, Pageable page);
}
