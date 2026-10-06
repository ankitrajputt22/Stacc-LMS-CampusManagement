package com.stacc.backend.auth.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.permission.Permission;
import com.stacc.backend.auth.role.Role;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * What Spring Security knows about a Stacc account: who it is, its password hash,
 * whether it may sign in, and what it is allowed to do. It holds no profile details.
 */
public final class StaccUserPrincipal implements UserDetails, CredentialsContainer {

    private static final String ROLE_PREFIX = "ROLE_";

    private final Long accountId;
    private final String loginId;
    private String passwordHash;
    private final boolean enabled;
    private final Set<GrantedAuthority> authorities;

    private StaccUserPrincipal(
            Long accountId, String loginId, String passwordHash, boolean enabled, Set<GrantedAuthority> authorities) {
        this.accountId = accountId;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    /** The account's roles and their permissions must already be loaded. */
    public static StaccUserPrincipal from(UserAccount account) {
        return new StaccUserPrincipal(
                account.getId(),
                account.getLoginId(),
                account.getPasswordHash(),
                account.getStatus() == AccountStatus.ACTIVE,
                authoritiesOf(account));
    }

    // A role becomes ROLE_<name>. A permission keeps its code. Shared permissions appear once.
    private static Set<GrantedAuthority> authoritiesOf(UserAccount account) {
        Set<String> names = new TreeSet<>();
        for (Role role : account.getRoles()) {
            names.add(ROLE_PREFIX + role.getName().name());
            for (Permission permission : role.getPermissions()) {
                names.add(permission.getCode());
            }
        }
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        names.forEach(name -> authorities.add(new SimpleGrantedAuthority(name)));
        return Collections.unmodifiableSet(authorities);
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getLoginId() {
        return loginId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** The account's role names without the ROLE_ prefix, in alphabetical order. */
    public List<String> getRoleNames() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
    }

    /** Spring Security's "username" is the Stacc login ID. */
    @Override
    public String getUsername() {
        return loginId;
    }

    /** Spring Security's "password" is the stored hash, never a plain password. */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** Spring Security calls this after a successful sign-in so the hash is not kept in memory. */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public String toString() {
        return "StaccUserPrincipal[accountId=" + accountId + ", loginId=" + loginId + ", enabled=" + enabled + "]";
    }
}
