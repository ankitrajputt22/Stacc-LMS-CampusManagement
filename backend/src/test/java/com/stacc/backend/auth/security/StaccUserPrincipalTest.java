package com.stacc.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.permission.Permission;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

class StaccUserPrincipalTest {

    private static final String PASSWORD_HASH = "example-hash-value";

    private static List<String> authorityNames(StaccUserPrincipal principal) {
        return principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void loginIdAndPasswordHashBecomeTheSpringUsernameAndPassword() {
        UserAccount account = new UserAccount("2408400100011", PASSWORD_HASH);
        ReflectionTestUtils.setField(account, "id", 42L);

        StaccUserPrincipal principal = StaccUserPrincipal.from(account);

        assertEquals(42L, principal.getAccountId());
        assertEquals("2408400100011", principal.getLoginId());
        assertEquals("2408400100011", principal.getUsername());
        assertEquals(PASSWORD_HASH, principal.getPassword());
    }

    @Test
    void activeAccountIsEnabled() {
        StaccUserPrincipal principal = StaccUserPrincipal.from(new UserAccount("EMP1024", PASSWORD_HASH));

        assertTrue(principal.isEnabled());
        assertTrue(principal.isAccountNonLocked());
        assertTrue(principal.isAccountNonExpired());
        assertTrue(principal.isCredentialsNonExpired());
    }

    @Test
    void disabledAccountIsNotEnabled() {
        UserAccount account = new UserAccount("EMP1024", PASSWORD_HASH);
        account.setStatus(AccountStatus.DISABLED);

        assertFalse(StaccUserPrincipal.from(account).isEnabled());
    }

    @Test
    void accountWithoutRolesHasNoAuthorities() {
        StaccUserPrincipal principal = StaccUserPrincipal.from(new UserAccount("EMP1024", PASSWORD_HASH));

        assertTrue(principal.getAuthorities().isEmpty());
    }

    @Test
    void rolesBecomeRoleAuthorities() {
        UserAccount student = new UserAccount("2408400100011", PASSWORD_HASH);
        student.assignRole(new Role(RoleName.STUDENT));
        UserAccount staff = new UserAccount("EMP1024", PASSWORD_HASH);
        staff.assignRole(new Role(RoleName.FACULTY));
        staff.assignRole(new Role(RoleName.ADMIN));

        assertEquals(List.of("ROLE_STUDENT"), authorityNames(StaccUserPrincipal.from(student)));
        assertEquals(List.of("ROLE_ADMIN", "ROLE_FACULTY"), authorityNames(StaccUserPrincipal.from(staff)));
    }

    @Test
    void permissionsBecomeAuthoritiesUsingTheirCode() {
        Role faculty = new Role(RoleName.FACULTY);
        faculty.assignPermission(new Permission("COURSE_VIEW"));
        faculty.assignPermission(new Permission("ATTENDANCE_MARK"));
        UserAccount account = new UserAccount("EMP1024", PASSWORD_HASH);
        account.assignRole(faculty);

        assertEquals(
                List.of("ATTENDANCE_MARK", "COURSE_VIEW", "ROLE_FACULTY"),
                authorityNames(StaccUserPrincipal.from(account)));
    }

    @Test
    void permissionSharedByTwoRolesAppearsOnce() {
        Role faculty = new Role(RoleName.FACULTY);
        faculty.assignPermission(new Permission("COURSE_VIEW"));
        Role admin = new Role(RoleName.ADMIN);
        admin.assignPermission(new Permission("COURSE_VIEW"));
        admin.assignPermission(new Permission("COURSE_MANAGE"));
        UserAccount account = new UserAccount("EMP1024", PASSWORD_HASH);
        account.assignRole(faculty);
        account.assignRole(admin);

        assertEquals(
                List.of("COURSE_MANAGE", "COURSE_VIEW", "ROLE_ADMIN", "ROLE_FACULTY"),
                authorityNames(StaccUserPrincipal.from(account)));
    }

    @Test
    void authoritiesCannotBeChanged() {
        UserAccount account = new UserAccount("EMP1024", PASSWORD_HASH);
        account.assignRole(new Role(RoleName.FACULTY));
        StaccUserPrincipal principal = StaccUserPrincipal.from(account);

        @SuppressWarnings("unchecked")
        var authorities = (java.util.Collection<GrantedAuthority>) principal.getAuthorities();
        assertThrows(
                UnsupportedOperationException.class,
                () -> authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void passwordHashIsNotInTheTextFormAndCanBeErased() {
        StaccUserPrincipal principal = StaccUserPrincipal.from(new UserAccount("EMP1024", PASSWORD_HASH));

        assertFalse(principal.toString().contains(PASSWORD_HASH));

        principal.eraseCredentials();

        assertNull(principal.getPassword());
    }
}
