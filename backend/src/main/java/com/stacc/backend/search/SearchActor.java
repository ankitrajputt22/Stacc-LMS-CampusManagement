package com.stacc.backend.search;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Who is searching: the account in the access token, every role it holds, and its permission codes. It is read from
 * the token only; a search request never names an account. No role is the main one: each section asks for the roles
 * and permissions its own domain routes need.
 */
public record SearchActor(Long accountId, Set<String> roles, Set<String> authorities) {

    private static final String ROLE_PREFIX = "ROLE_";

    public SearchActor {
        roles = Set.copyOf(roles);
        authorities = Set.copyOf(authorities);
    }

    public static SearchActor of(Long accountId, Collection<? extends GrantedAuthority> granted) {
        Set<String> names = AuthorityUtils.authorityListToSet(granted);
        Set<String> roles = names.stream()
                .filter(name -> name.startsWith(ROLE_PREFIX))
                .map(name -> name.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toSet());
        Set<String> permissions = names.stream().filter(name -> !name.startsWith(ROLE_PREFIX)).collect(Collectors.toSet());
        return new SearchActor(accountId, roles, permissions);
    }

    public boolean holds(String role) {
        return roles.contains(role);
    }

    public boolean can(String permission) {
        return authorities.contains(permission);
    }

    /** The role together with the permission, as a domain route rule {@code hasRole(..) and hasAuthority(..)} reads. */
    public boolean holdsWith(String role, String... permissions) {
        if (!holds(role)) {
            return false;
        }
        for (String permission : permissions) {
            if (!can(permission)) {
                return false;
            }
        }
        return true;
    }

    /** Any of the three roles, as {@code hasAnyRole('STUDENT', 'FACULTY', 'ADMIN')} reads. */
    public boolean holdsAnyCollegeRole() {
        return holds("STUDENT") || holds("FACULTY") || holds("ADMIN");
    }

    /** The roles and permissions again as Spring authorities, for a domain actor such as {@code HelpdeskActor.of}. */
    public Collection<? extends GrantedAuthority> grantedAuthorities() {
        return AuthorityUtils.createAuthorityList(Stream.concat(roles.stream().map(role -> ROLE_PREFIX + role),
                authorities.stream()).toArray(String[]::new));
    }
}
