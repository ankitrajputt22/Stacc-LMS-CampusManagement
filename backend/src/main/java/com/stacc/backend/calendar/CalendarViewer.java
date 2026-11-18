package com.stacc.backend.calendar;

import java.util.Collection;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

public record CalendarViewer(Long userAccountId, boolean student, boolean faculty, boolean admin) {

    public static CalendarViewer of(Long userAccountId, Collection<? extends GrantedAuthority> authorities) {
        Set<String> names = AuthorityUtils.authorityListToSet(authorities);
        return new CalendarViewer(userAccountId, names.contains("ROLE_STUDENT"), names.contains("ROLE_FACULTY"),
                names.contains("ROLE_ADMIN"));
    }
}
