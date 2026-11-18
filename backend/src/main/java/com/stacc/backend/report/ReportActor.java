package com.stacc.backend.report;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

public record ReportActor(Long accountId, Set<String> roles, Set<String> authorities) {

    private static final String ROLE_PREFIX = "ROLE_";

    public ReportActor {
        roles = Set.copyOf(roles);
        authorities = Set.copyOf(authorities);
    }

    public static ReportActor of(Long accountId, Collection<? extends GrantedAuthority> granted) {
        Set<String> names = AuthorityUtils.authorityListToSet(granted);
        return new ReportActor(accountId,
                names.stream().filter(name -> name.startsWith(ROLE_PREFIX)).map(name -> name.substring(ROLE_PREFIX.length()))
                        .collect(Collectors.toSet()),
                names.stream().filter(name -> !name.startsWith(ROLE_PREFIX)).collect(Collectors.toSet()));
    }

    public boolean holdsWith(String role, String permission) {
        return roles.contains(role) && authorities.contains(permission);
    }

    public Collection<? extends GrantedAuthority> grantedAuthorities() {
        return AuthorityUtils.createAuthorityList(Stream.concat(roles.stream().map(role -> ROLE_PREFIX + role),
                authorities.stream()).toArray(String[]::new));
    }
}
