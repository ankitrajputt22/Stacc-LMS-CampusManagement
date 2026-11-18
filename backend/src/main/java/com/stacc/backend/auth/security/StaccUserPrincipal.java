package com.stacc.backend.auth.security;

import java.time.Instant;
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

public final class StaccUserPrincipal implements UserDetails, CredentialsContainer {

    private static final String ROLE_PREFIX = "ROLE_";

    private final Long accountId;
    private final String loginId;
    private String passwordHash;
    private final boolean enabled;
    private final boolean passwordChangeRequired;
    private final boolean credentialsNonExpired;
    private final Set<GrantedAuthority> authorities;

    private StaccUserPrincipal(Long accountId, String loginId, String passwordHash, boolean enabled,
            boolean passwordChangeRequired, boolean credentialsNonExpired, Set<GrantedAuthority> authorities) {
        this.accountId = accountId;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.passwordChangeRequired = passwordChangeRequired;
        this.credentialsNonExpired = credentialsNonExpired;
        this.authorities = authorities;
    }

    public static StaccUserPrincipal from(UserAccount account) {
        return from(account, Instant.now());
    }

    public static StaccUserPrincipal from(UserAccount account, Instant now) {
        return new StaccUserPrincipal(
                account.getId(),
                account.getLoginId(),
                account.getPasswordHash(),
                account.getStatus() == AccountStatus.ACTIVE,
                account.isPasswordChangeRequired(),
                !account.temporaryPasswordExpiredAt(now),
                authoritiesOf(account));
    }

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

    public List<String> getRoleNames() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public String toString() {
        return "StaccUserPrincipal[accountId=" + accountId + ", loginId=" + loginId + ", enabled=" + enabled + "]";
    }
}
