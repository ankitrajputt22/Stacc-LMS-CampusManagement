package com.stacc.backend.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Sessions on a real database: a started session is accepted, and signing out, signing out everywhere, idleness, and
 * a disabled account each end acceptance at once. Uses the demo account facultyadmin1, which no other MySQL test uses.
 */
class SessionMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private SessionService sessions;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sessionsEndForEveryReasonAndStayEnded() {
        Long account = accounts.findByLoginId("facultyadmin1").orElseThrow().getId();
        UserSession laptop = sessions.start(account, "Mozilla/5.0 (Macintosh; Intel Mac OS X) Chrome/130");
        UserSession phone = sessions.start(account, "Mozilla/5.0 (Linux; Android 14) Chrome/130");
        UserSession tablet = sessions.start(account, null);

        assertThat(sessions.isActive(laptop.getId(), account)).isTrue();
        assertThat(sessions.isActive(laptop.getId(), account + 1)).as("another account's session").isFalse();
        assertThat(sessions.isActive("no-such-session", account)).isFalse();

        sessions.end(laptop.getId(), account, SessionEndReason.SIGNED_OUT);
        assertThat(sessions.isActive(laptop.getId(), account)).isFalse();
        assertThat(sessions.isActive(phone.getId(), account)).isTrue();

        // Idle for longer than the timeout: refused, and ended for good.
        // Moved back inside MySQL, so no time zone of this machine can get in the way.
        jdbc.update("update user_sessions set last_seen_at = date_sub(last_seen_at, interval 3 hour) where id = ?",
                tablet.getId());
        assertThat(sessions.isActive(tablet.getId(), account)).isFalse();
        assertThat(jdbc.queryForObject("select end_reason from user_sessions where id = ?", String.class, tablet.getId()))
                .isEqualTo("IDLE");

        assertThat(sessions.endAll(account, SessionEndReason.SIGNED_OUT_EVERYWHERE)).isEqualTo(1);
        assertThat(sessions.isActive(phone.getId(), account)).isFalse();
        assertThat(sessions.openSessions(account)).isEmpty();
    }

    @Test
    void disablingTheAccountRefusesItsOpenSessionsAtOnce() {
        UserAccount account = accounts.findByLoginId("faculty2").orElseThrow();
        UserSession session = sessions.start(account.getId(), null);
        assertThat(sessions.isActive(session.getId(), account.getId())).isTrue();

        jdbc.update("update user_accounts set status = ? where id = ?", AccountStatus.DISABLED.name(), account.getId());
        try {
            assertThat(sessions.isActive(session.getId(), account.getId())).isFalse();
        } finally {
            jdbc.update("update user_accounts set status = ? where id = ?", AccountStatus.ACTIVE.name(), account.getId());
        }
    }
}
