package com.stacc.backend.auth.session;

import com.stacc.backend.auth.account.AccountStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-in sessions: started at login, checked on every request, ended by signing out, by idleness, or by the college.
 * The check is the authority: a token whose session is not open, or whose account is no longer active, is refused,
 * however long the token itself would still be valid.
 */
@Service
@EnableConfigurationProperties(SessionProperties.class)
public class SessionService {

    // "Last seen" is written at most this often per session, so a busy page does not write on every request.
    static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final UserSessionRepository sessions;
    private final SessionProperties properties;
    private final Clock clock;

    public SessionService(UserSessionRepository sessions, SessionProperties properties, Clock clock) {
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a session for an account that has just signed in. */
    @Transactional
    public UserSession start(Long accountId, String userAgent) {
        Instant now = clock.instant();
        return sessions.saveAndFlush(new UserSession(accountId, Devices.describe(userAgent), now,
                now.plus(properties.maxDuration())));
    }

    /**
     * Whether a request naming this session for this account may go through. An idle session is ended on the way, so
     * it never comes back; anything unknown, ended, expired, foreign, or of an inactive account is refused.
     */
    @Transactional
    public boolean isActive(String sessionId, Long accountId) {
        if (sessionId == null || accountId == null) {
            return false;
        }
        Instant now = clock.instant();
        UserSessionRepository.SessionWithStatus found = sessions.findWithAccountStatus(sessionId).orElse(null);
        if (found == null || !found.session().getUserAccountId().equals(accountId)
                || found.accountStatus() != AccountStatus.ACTIVE) {
            return false;
        }
        UserSession session = found.session();
        if (!session.isOpenAt(now, properties.idleTimeout())) {
            if (session.getEndedAt() == null && now.isBefore(session.getExpiresAt())) {
                session.end(now, SessionEndReason.IDLE);
            }
            return false;
        }
        sessions.touch(sessionId, now, now.minus(TOUCH_INTERVAL));
        return true;
    }

    /** Ends one session of an account. Ending it again, or a session of another account, changes nothing. */
    @Transactional
    public void end(String sessionId, Long accountId, SessionEndReason reason) {
        sessions.findById(sessionId)
                .filter(session -> session.getUserAccountId().equals(accountId))
                .ifPresent(session -> session.end(clock.instant(), reason));
    }

    /** Ends every open session of an account; returns how many were open. */
    @Transactional
    public int endAll(Long accountId, SessionEndReason reason) {
        return sessions.endAllOf(accountId, null, clock.instant(), reason);
    }

    /** The account's open sessions, newest first. */
    @Transactional(readOnly = true)
    public List<UserSession> openSessions(Long accountId) {
        Instant now = clock.instant();
        return sessions.findByUserAccountIdAndEndedAtIsNullOrderByCreatedAtDesc(accountId).stream()
                .filter(session -> session.isOpenAt(now, properties.idleTimeout()))
                .toList();
    }

    public SessionProperties properties() {
        return properties;
    }
}
