package com.stacc.backend.auth.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.session.SessionEndReason;
import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.common.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final String CURRENT = "old river lamp 42";
    private static final String NEXT = "new cocoa window 77";

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final SessionService sessions = mock(SessionService.class);
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final PasswordService service =
            new PasswordService(accounts, encoder, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    private UserAccount account;

    @BeforeEach
    void anAccount() {
        account = new UserAccount("EMP1024", encoder.encode(CURRENT));
        ReflectionTestUtils.setField(account, "id", 7L);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
    }

    @Test
    void changingStoresOnlyANewHashClearsTheRequirementAndEndsOtherSessions() {
        account.issueTemporaryPassword(encoder.encode(CURRENT), NOW.plus(Duration.ofDays(1)));

        service.changeOwnPassword(7L, "this-session", CURRENT, NEXT);

        assertThat(account.getPasswordHash()).isNotEqualTo(NEXT);
        assertThat(encoder.matches(NEXT, account.getPasswordHash())).isTrue();
        assertThat(account.isPasswordChangeRequired()).isFalse();
        assertThat(account.getPasswordChangedAt()).isEqualTo(NOW);
        verify(accounts).save(account);
        verify(sessions).endAllExcept(7L, "this-session", SessionEndReason.PASSWORD_CHANGED);
    }

    @Test
    void wrongCurrentPasswordChangesNothing() {
        String before = account.getPasswordHash();

        assertThatThrownBy(() -> service.changeOwnPassword(7L, "s", "not it at all", NEXT))
                .isInstanceOf(ApiException.class).hasMessage(PasswordService.WRONG_CURRENT);

        assertThat(account.getPasswordHash()).isEqualTo(before);
        verify(sessions, never()).endAllExcept(any(), any(), any());
    }

    @Test
    void weakOrUnchangedNewPasswordIsRefused() {
        assertThatThrownBy(() -> service.changeOwnPassword(7L, "s", CURRENT, "password123"))
                .isInstanceOf(ApiException.class).hasMessageContaining("too easy to guess");
        assertThatThrownBy(() -> service.changeOwnPassword(7L, "s", CURRENT, CURRENT))
                .isInstanceOf(ApiException.class).hasMessage(PasswordService.SAME_AS_CURRENT);
        verify(accounts, never()).save(any());
    }

    @Test
    void temporaryPasswordRequiresAChangeExpiresAndEndsEverySession() {
        service.issueTemporaryPassword(7L, "temporary 123 pass", null);

        assertThat(account.isPasswordChangeRequired()).isTrue();
        assertThat(encoder.matches("temporary 123 pass", account.getPasswordHash())).isTrue();
        assertThat(account.temporaryPasswordExpiredAt(NOW.plus(PasswordService.TEMPORARY_VALIDITY))).isTrue();
        assertThat(account.temporaryPasswordExpiredAt(NOW.plus(Duration.ofDays(2)))).isFalse();
        assertThat(service.isChangeRequired(7L)).isTrue();
        verify(sessions).endAll(7L, SessionEndReason.ENDED_BY_COLLEGE);
    }
}
