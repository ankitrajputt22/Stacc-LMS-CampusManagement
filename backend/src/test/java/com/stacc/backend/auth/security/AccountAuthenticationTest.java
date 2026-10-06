package com.stacc.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Signs in through the authentication manager exactly as the future sign-in endpoint will,
 * with the account repository replaced by a stand-in so no database is needed.
 */
class AccountAuthenticationTest {

    // Made-up values used only by these tests.
    private static final String LOGIN_ID = "EMP1024";
    private static final String PASSWORD = "TestPassword123!";
    private static final String WRONG_PASSWORD = "WrongPassword123!";

    private final SecurityConfig securityConfig = new SecurityConfig();
    private final PasswordEncoder passwordEncoder = securityConfig.passwordEncoder();
    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final AuthenticationManager authenticationManager = securityConfig.authenticationManager(
            new StaccUserDetailsService(repository), passwordEncoder);

    private UserAccount account;

    @BeforeEach
    void setUp() {
        account = new UserAccount(LOGIN_ID, passwordEncoder.encode(PASSWORD));
        account.assignRole(new Role(RoleName.FACULTY));
        when(repository.findWithRolesAndPermissionsByLoginId(anyString())).thenReturn(Optional.empty());
        when(repository.findWithRolesAndPermissionsByLoginId(LOGIN_ID)).thenReturn(Optional.of(account));
    }

    private Authentication signIn(String loginId, String password) {
        return authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(loginId, password));
    }

    @Test
    void correctLoginIdAndPasswordSignIn() {
        Authentication authentication = signIn(LOGIN_ID, PASSWORD);

        assertTrue(authentication.isAuthenticated());
        StaccUserPrincipal principal = assertInstanceOf(StaccUserPrincipal.class, authentication.getPrincipal());
        assertEquals(LOGIN_ID, principal.getLoginId());
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_FACULTY")));
    }

    @Test
    void passwordAndHashAreNotKeptAfterSignIn() {
        Authentication authentication = signIn(LOGIN_ID, PASSWORD);

        assertNull(authentication.getCredentials());
        assertNull(((StaccUserPrincipal) authentication.getPrincipal()).getPassword());
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThrows(BadCredentialsException.class, () -> signIn(LOGIN_ID, WRONG_PASSWORD));
    }

    @Test
    void unknownLoginIdIsRejectedTheSameWayAsAWrongPassword() {
        assertThrows(BadCredentialsException.class, () -> signIn("EMP9999", PASSWORD));
    }

    @Test
    void disabledAccountCannotSignInEvenWithTheCorrectPassword() {
        account.setStatus(AccountStatus.DISABLED);

        assertThrows(DisabledException.class, () -> signIn(LOGIN_ID, PASSWORD));
    }

    @Test
    void disabledAccountWithAWrongPasswordLooksLikeAnyOtherWrongLogin() {
        account.setStatus(AccountStatus.DISABLED);

        assertThrows(BadCredentialsException.class, () -> signIn(LOGIN_ID, WRONG_PASSWORD));
    }
}
