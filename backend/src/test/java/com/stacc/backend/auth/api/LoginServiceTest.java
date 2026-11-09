package com.stacc.backend.auth.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.permission.Permission;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.security.StaccUserPrincipal;
import com.stacc.backend.auth.token.AccessToken;
import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.auth.session.UserSession;
import com.stacc.backend.auth.token.AccessTokenService;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.stacc.backend.auth.throttle.SignInThrottleService;
import org.springframework.test.util.ReflectionTestUtils;

class LoginServiceTest {

    // Made-up values used only by these tests.
    private static final String LOGIN_ID = "EMP1024";
    private static final String PASSWORD = " Test Password 123! ";

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final AccessTokenService accessTokenService = mock(AccessTokenService.class);
    private final SessionService sessions = mock(SessionService.class);
    private final SignInThrottleService throttle = throttleThatAllows();
    private final LoginService loginService = new LoginService(authenticationManager, accessTokenService, sessions, throttle);
    private static final String ADDRESS = "203.0.113.5";

    private static SignInThrottleService throttleThatAllows() {
        SignInThrottleService allowing = mock(SignInThrottleService.class);
        org.mockito.Mockito.when(allowing.waitFor(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.time.Duration.ZERO);
        return allowing;
    }
    private static final Instant SESSION_END = Instant.parse("2026-11-01T22:00:00Z");
    private static final UserSession SESSION = new UserSession(12L, "Chrome on Android",
            SESSION_END.minusSeconds(12 * 3600), SESSION_END);

    private static StaccUserPrincipal principalWithRoles(RoleName... roleNames) {
        UserAccount account = new UserAccount(LOGIN_ID, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", 12L);
        for (RoleName roleName : roleNames) {
            Role role = new Role(roleName);
            role.assignPermission(new Permission("COURSE_VIEW"));
            account.assignRole(role);
        }
        return StaccUserPrincipal.from(account);
    }

    private void authenticationSucceedsWith(StaccUserPrincipal principal) {
        when(authenticationManager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(sessions.start(12L, "Mozilla/5.0")).thenReturn(SESSION);
        when(accessTokenService.issue(principal, SESSION.getId(), SESSION_END))
                .thenReturn(new AccessToken("signed-token-value", 12 * 3600));
    }

    @Test
    void loginIdAndPasswordAreCheckedByTheAuthenticationManagerExactlyAsSent() {
        authenticationSucceedsWith(principalWithRoles(RoleName.FACULTY));

        loginService.login(new LoginRequest(LOGIN_ID, PASSWORD), "Mozilla/5.0", ADDRESS);

        ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(attempt.capture());
        assertFalse(attempt.getValue().isAuthenticated());
        assertEquals(LOGIN_ID, attempt.getValue().getPrincipal());
        // The password keeps its spaces: it is never trimmed or changed.
        assertEquals(PASSWORD, attempt.getValue().getCredentials());
    }

    @Test
    void successfulLoginReturnsTheAccountAndItsSortedRoleNames() {
        authenticationSucceedsWith(principalWithRoles(RoleName.FACULTY, RoleName.ADMIN));

        SessionResponse response = loginService.login(new LoginRequest(LOGIN_ID, PASSWORD), "Mozilla/5.0", ADDRESS).session();

        assertEquals(12L, response.accountId());
        assertEquals(SESSION_END, response.expiresAt());
        assertEquals(LOGIN_ID, response.loginId());
        assertEquals(List.of("ADMIN", "FACULTY"), response.roles());
    }

    @Test
    void successfulLoginStartsASessionAndIssuesItsToken() {
        StaccUserPrincipal principal = principalWithRoles(RoleName.STUDENT);
        authenticationSucceedsWith(principal);

        LoginService.SignIn signIn = loginService.login(new LoginRequest(LOGIN_ID, PASSWORD), "Mozilla/5.0", ADDRESS);

        verify(sessions).start(12L, "Mozilla/5.0");
        verify(accessTokenService).issue(principal, SESSION.getId(), SESSION_END);
        assertEquals("signed-token-value", signIn.token());
        assertEquals(java.time.Duration.ofHours(12), signIn.lifetime());
        assertFalse(signIn.toString().contains("signed-token-value"));
        assertFalse(signIn.session().toString().contains("signed-token-value"));
    }

    @Test
    void wrongCredentialsAndDisabledAccountsFailWithTheSameSafeMessage() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        ApiException wrongCredentials =
                assertThrows(ApiException.class, () -> loginService.login(new LoginRequest(LOGIN_ID, PASSWORD), "Mozilla/5.0", ADDRESS));

        AuthenticationManager disabledManager = mock(AuthenticationManager.class);
        when(disabledManager.authenticate(any())).thenThrow(new DisabledException("User is disabled"));
        ApiException disabled = assertThrows(
                ApiException.class,
                () -> new LoginService(disabledManager, accessTokenService, sessions, throttle)
                        .login(new LoginRequest(LOGIN_ID, PASSWORD), "Mozilla/5.0", ADDRESS));

        assertEquals(HttpStatus.UNAUTHORIZED, wrongCredentials.getStatus());
        assertEquals("Invalid login ID or password.", wrongCredentials.getMessage());
        assertEquals(wrongCredentials.getStatus(), disabled.getStatus());
        assertEquals(wrongCredentials.getMessage(), disabled.getMessage());
    }

    @Test
    void failedLoginNeverGetsAToken() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(ApiException.class, () -> loginService.login(new LoginRequest(LOGIN_ID, PASSWORD), "Mozilla/5.0", ADDRESS));

        verifyNoInteractions(accessTokenService, sessions);
    }

    @Test
    void realServerProblemsAreNotTurnedIntoAFailedLogin() {
        InternalAuthenticationServiceException databaseDown =
                new InternalAuthenticationServiceException("database is unavailable");
        when(authenticationManager.authenticate(any())).thenThrow(databaseDown);

        InternalAuthenticationServiceException thrown = assertThrows(
                InternalAuthenticationServiceException.class,
                () -> loginService.login(new LoginRequest(LOGIN_ID, PASSWORD), "Mozilla/5.0", ADDRESS));

        assertSame(databaseDown, thrown);
    }

    @Test
    void loginRequestTextFormLeavesOutThePassword() {
        assertFalse(new LoginRequest(LOGIN_ID, PASSWORD).toString().contains(PASSWORD));
    }

    @Test
    void aBlockedSignInIsRefusedBeforeThePasswordIsChecked() {
        org.mockito.Mockito.when(throttle.waitFor(LOGIN_ID, ADDRESS)).thenReturn(java.time.Duration.ofSeconds(30));

        ApiException refused = assertThrows(ApiException.class,
                () -> loginService.login(new LoginRequest(LOGIN_ID, PASSWORD), "Mozilla/5.0", ADDRESS));

        assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, refused.getStatus());
        assertEquals(LoginService.TOO_MANY_MESSAGE, refused.getMessage());
        org.mockito.Mockito.verifyNoInteractions(authenticationManager, sessions);
    }

    @Test
    void aFailureIsCountedAndASuccessForgetsTheLoginIdsFailures() {
        org.mockito.Mockito.when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("wrong"));
        assertThrows(ApiException.class, () -> loginService.login(new LoginRequest(LOGIN_ID, PASSWORD), "Mozilla/5.0", ADDRESS));
        org.mockito.Mockito.verify(throttle).recordFailure(LOGIN_ID, ADDRESS);
        org.mockito.Mockito.verify(throttle, org.mockito.Mockito.never()).recordSuccess(org.mockito.ArgumentMatchers.any());
    }
}
