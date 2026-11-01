package com.stacc.backend.auth.session;

import com.stacc.backend.auth.account.AccountStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    /** The session and its account's status, in one query, for the check made on every request. */
    @Query("""
            select new com.stacc.backend.auth.session.UserSessionRepository$SessionWithStatus(session, account.status)
            from UserSession session, UserAccount account
            where session.id = :sessionId and account.id = session.userAccountId
            """)
    Optional<SessionWithStatus> findWithAccountStatus(@Param("sessionId") String sessionId);

    /** Moves "last seen" forward, at most once a minute per session, without loading the entity. */
    @Modifying
    @Query("""
            update UserSession session set session.lastSeenAt = :now
            where session.id = :sessionId and session.endedAt is null and session.lastSeenAt < :before
            """)
    int touch(@Param("sessionId") String sessionId, @Param("now") Instant now, @Param("before") Instant before);

    /** Ends every open session of an account, except one when given (null ends them all). */
    @Modifying
    @Query("""
            update UserSession session set session.endedAt = :now, session.endReason = :reason
            where session.userAccountId = :accountId and session.endedAt is null
              and (:keepSessionId is null or session.id <> :keepSessionId)
            """)
    int endAllOf(@Param("accountId") Long accountId, @Param("keepSessionId") String keepSessionId,
            @Param("now") Instant now, @Param("reason") SessionEndReason reason);

    List<UserSession> findByUserAccountIdAndEndedAtIsNullOrderByCreatedAtDesc(Long userAccountId);

    record SessionWithStatus(UserSession session, AccountStatus accountStatus) {
    }
}
