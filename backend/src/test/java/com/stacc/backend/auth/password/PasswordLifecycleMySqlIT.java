package com.stacc.backend.auth.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.api.LoginRequest;
import com.stacc.backend.auth.api.LoginService;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.auth.session.UserSession;
import com.stacc.backend.common.error.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Passwords on a real database: changing one's own password ends the account's other sessions, and a temporary
 * password forces a change and stops working once it expires. Uses its own account, so no other test is affected.
 */
class PasswordLifecycleMySqlIT extends MySqlIntegrationTest {

    private static final String LOGIN_ID = "password-lifecycle-it";
    private static final String FIRST = "first river lamp 42";
    private static final String SECOND = "second cocoa window 77";
    private static final String TEMPORARY = "temporary harbour 19";

    @Autowired
    private PasswordService passwords;

    @Autowired
    private LoginService login;

    @Autowired
    private SessionService sessions;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void changingAndTemporaryPasswordsFollowTheLifecycle() {
        Long id = accounts.saveAndFlush(new UserAccount(LOGIN_ID, encoder.encode(FIRST))).getId();

        LoginService.SignIn signIn = login.login(new LoginRequest(LOGIN_ID, FIRST), null, "198.51.100.7");
        assertThat(signIn.session().passwordChangeRequired()).isFalse();
        String thisSession = sessions.openSessions(id).getFirst().getId();
        UserSession otherDevice = sessions.start(id, null);

        passwords.changeOwnPassword(id, thisSession, FIRST, SECOND);

        assertThat(sessions.check(thisSession, id).active()).isTrue();
        assertThat(sessions.check(otherDevice.getId(), id).active()).isFalse();
        assertThat(jdbc.queryForObject("select end_reason from user_sessions where id = ?", String.class,
                otherDevice.getId())).isEqualTo("PASSWORD_CHANGED");
        assertThat(jdbc.queryForObject("select password_changed_at is not null from user_accounts where id = ?",
                Boolean.class, id)).isTrue();
        assertThatThrownBy(() -> login.login(new LoginRequest(LOGIN_ID, FIRST), null, "198.51.100.7")).isInstanceOf(ApiException.class);

        passwords.issueTemporaryPassword(id, TEMPORARY, Duration.ofDays(1));
        assertThat(sessions.openSessions(id)).as("every session ends").isEmpty();

        LoginService.SignIn temporary = login.login(new LoginRequest(LOGIN_ID, TEMPORARY), null, "198.51.100.7");
        assertThat(temporary.session().passwordChangeRequired()).isTrue();
        String forced = sessions.openSessions(id).getFirst().getId();
        assertThat(sessions.check(forced, id)).isEqualTo(new SessionService.SessionCheck(true, true));

        // Moved back inside MySQL, so no time zone of this machine can get in the way.
        jdbc.update("update user_accounts set temporary_password_expires_at = "
                + "date_sub(temporary_password_expires_at, interval 2 day) where id = ?", id);
        assertThatThrownBy(() -> login.login(new LoginRequest(LOGIN_ID, TEMPORARY), null, "198.51.100.7"))
                .isInstanceOf(ApiException.class).hasMessage("Invalid login ID or password.");
    }
}
