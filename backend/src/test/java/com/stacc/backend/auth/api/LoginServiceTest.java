package com.stacc.backend.auth.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.permission.Permission;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.security.StaccUserPrincipal;
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
import org.springframework.test.util.ReflectionTestUtils;

class LoginServiceTest {

    // Made-up values used only by these tests.
    private static final String LOGIN_ID = "EMP1024";
    private static final String PASSWORD = " Test Password 123! ";

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final LoginService loginService = new LoginService(authenticationManager);

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
    }

    @Test
    void loginIdAndPasswordAreCheckedByTheAuthenticationManagerExactlyAsSent() {
        authenticationSucceedsWith(principalWithRoles(RoleName.FACULTY));

        loginService.login(new LoginRequest(LOGIN_ID, PASSWORD));

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

        LoginResponse response = loginService.login(new LoginRequest(LOGIN_ID, PASSWORD));

        assertEquals(12L, response.accountId());
        assertEquals(LOGIN_ID, response.loginId());
        assertEquals(List.of("ADMIN", "FACULTY"), response.roles());
    }

    @Test
    void wrongCredentialsAndDisabledAccountsFailWithTheSameSafeMessage() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        ApiException wrongCredentials =
                assertThrows(ApiException.class, () -> loginService.login(new LoginRequest(LOGIN_ID, PASSWORD)));

        AuthenticationManager disabledManager = mock(AuthenticationManager.class);
        when(disabledManager.authenticate(any())).thenThrow(new DisabledException("User is disabled"));
        ApiException disabled = assertThrows(
                ApiException.class,
                () -> new LoginService(disabledManager).login(new LoginRequest(LOGIN_ID, PASSWORD)));

        assertEquals(HttpStatus.UNAUTHORIZED, wrongCredentials.getStatus());
        assertEquals("Invalid login ID or password.", wrongCredentials.getMessage());
        assertEquals(wrongCredentials.getStatus(), disabled.getStatus());
        assertEquals(wrongCredentials.getMessage(), disabled.getMessage());
    }

    @Test
    void realServerProblemsAreNotTurnedIntoAFailedLogin() {
        InternalAuthenticationServiceException databaseDown =
                new InternalAuthenticationServiceException("database is unavailable");
        when(authenticationManager.authenticate(any())).thenThrow(databaseDown);

        InternalAuthenticationServiceException thrown = assertThrows(
                InternalAuthenticationServiceException.class,
                () -> loginService.login(new LoginRequest(LOGIN_ID, PASSWORD)));

        assertSame(databaseDown, thrown);
    }

    @Test
    void loginRequestTextFormLeavesOutThePassword() {
        assertFalse(new LoginRequest(LOGIN_ID, PASSWORD).toString().contains(PASSWORD));
    }
}
