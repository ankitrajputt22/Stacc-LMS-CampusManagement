package com.stacc.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class StaccUserDetailsServiceTest {

    private static final String PASSWORD_HASH = "example-hash-value";

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private final StaccUserDetailsService service =
            new StaccUserDetailsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void temporaryPasswordWorksUntilItExpiresAndThenCountsAsExpiredCredentials() {
        UserAccount fresh = new UserAccount("EMP1024", PASSWORD_HASH);
        fresh.issueTemporaryPassword(PASSWORD_HASH, NOW.plus(Duration.ofMinutes(1)));
        UserAccount stale = new UserAccount("EMP2048", PASSWORD_HASH);
        stale.issueTemporaryPassword(PASSWORD_HASH, NOW);
        when(repository.findWithRolesAndPermissionsByLoginId("EMP1024")).thenReturn(Optional.of(fresh));
        when(repository.findWithRolesAndPermissionsByLoginId("EMP2048")).thenReturn(Optional.of(stale));

        StaccUserPrincipal usable = (StaccUserPrincipal) service.loadUserByUsername("EMP1024");
        StaccUserPrincipal expired = (StaccUserPrincipal) service.loadUserByUsername("EMP2048");

        assertTrue(usable.isCredentialsNonExpired());
        assertTrue(usable.isPasswordChangeRequired());
        assertFalse(expired.isCredentialsNonExpired());
    }

    @Test
    void existingAccountIsLoadedByLoginId() {
        UserAccount account = new UserAccount("EMP1024", PASSWORD_HASH);
        account.assignRole(new Role(RoleName.FACULTY));
        when(repository.findWithRolesAndPermissionsByLoginId("EMP1024")).thenReturn(Optional.of(account));

        UserDetails user = service.loadUserByUsername("EMP1024");

        assertInstanceOf(StaccUserPrincipal.class, user);
        assertEquals("EMP1024", user.getUsername());
        assertEquals(PASSWORD_HASH, user.getPassword());
        assertTrue(user.isEnabled());
        assertTrue(user.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_FACULTY")));
    }

    @Test
    void surroundingSpacesInTheLoginIdAreIgnored() {
        when(repository.findWithRolesAndPermissionsByLoginId("EMP1024"))
                .thenReturn(Optional.of(new UserAccount("EMP1024", PASSWORD_HASH)));

        assertEquals("EMP1024", service.loadUserByUsername("  EMP1024 ").getUsername());
        verify(repository).findWithRolesAndPermissionsByLoginId("EMP1024");
    }

    @Test
    void unknownLoginIdIsReportedAsNotFoundWithoutEchoingIt() {
        when(repository.findWithRolesAndPermissionsByLoginId(anyString())).thenReturn(Optional.empty());

        UsernameNotFoundException exception =
                assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("EMP9999"));

        assertFalse(exception.getMessage().contains("EMP9999"));
    }

    @Test
    void missingLoginIdIsNotFoundWithoutAskingTheDatabase() {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(null));
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("   "));

        verifyNoInteractions(repository);
    }
}
