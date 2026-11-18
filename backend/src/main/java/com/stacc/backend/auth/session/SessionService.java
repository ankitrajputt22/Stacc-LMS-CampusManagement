package com.stacc.backend.auth.session;

import com.stacc.backend.auth.account.AccountStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableConfigurationProperties(SessionProperties.class)
public class SessionService {

    static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final UserSessionRepository sessions;
    private final SessionProperties properties;
    private final Clock clock;

    public SessionService(UserSessionRepository sessions, SessionProperties properties, Clock clock) {
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public UserSession start(Long accountId, String userAgent) {
        Instant now = clock.instant();
        return sessions.saveAndFlush(new UserSession(accountId, Devices.describe(userAgent), now,
                now.plus(properties.maxDuration())));
    }

    public record SessionCheck(boolean active, boolean passwordChangeRequired) {

        public static final SessionCheck REFUSED = new SessionCheck(false, false);
    }

    @Transactional
    public SessionCheck check(String sessionId, Long accountId) {
        if (sessionId == null || accountId == null) {
            return SessionCheck.REFUSED;
        }
        Instant now = clock.instant();
        UserSessionRepository.SessionWithStatus found = sessions.findWithAccountStatus(sessionId).orElse(null);
        if (found == null || !found.session().getUserAccountId().equals(accountId)
                || found.accountStatus() != AccountStatus.ACTIVE) {
            return SessionCheck.REFUSED;
        }
        UserSession session = found.session();
        if (!session.isOpenAt(now, properties.idleTimeout())) {
            if (session.getEndedAt() == null && now.isBefore(session.getExpiresAt())) {
                session.end(now, SessionEndReason.IDLE);
            }
            return SessionCheck.REFUSED;
        }
        sessions.touch(sessionId, now, now.minus(TOUCH_INTERVAL));
        return new SessionCheck(true, found.passwordChangeRequired());
    }

    @Transactional
    public int endAllExcept(Long accountId, String keepSessionId, SessionEndReason reason) {
        return sessions.endAllOf(accountId, keepSessionId, clock.instant(), reason);
    }

    @Transactional
    public void end(String sessionId, Long accountId, SessionEndReason reason) {
        sessions.findById(sessionId)
                .filter(session -> session.getUserAccountId().equals(accountId))
                .ifPresent(session -> session.end(clock.instant(), reason));
    }

    @Transactional
    public int endAll(Long accountId, SessionEndReason reason) {
        return sessions.endAllOf(accountId, null, clock.instant(), reason);
    }

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
