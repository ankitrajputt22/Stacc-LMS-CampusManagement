package com.stacc.backend.campus;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Who is using the campus API: the account in the access token and every role code it holds. No role is the main one;
 * services and notices match any of them. It is read from the token only.
 */
public record CampusActor(Long accountId, Set<String> roleCodes) {

    private static final String ROLE_PREFIX = "ROLE_";

    public CampusActor {
        roleCodes = Set.copyOf(roleCodes);
    }

    public static CampusActor of(Long accountId, Collection<? extends GrantedAuthority> authorities) {
        return new CampusActor(accountId, AuthorityUtils.authorityListToSet(authorities).stream()
                .filter(name -> name.startsWith(ROLE_PREFIX))
                .map(name -> name.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toSet()));
    }

    public boolean holds(String roleCode) {
        return roleCodes.contains(roleCode);
    }
}
