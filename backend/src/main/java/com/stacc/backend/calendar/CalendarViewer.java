package com.stacc.backend.calendar;

import java.util.Collection;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Who is reading the calendar: the account in the access token, and every role it holds that the calendar knows. An
 * account may hold several; each one adds its own part, and none is the main one.
 */
public record CalendarViewer(Long userAccountId, boolean student, boolean faculty, boolean admin) {

    /** The viewer behind a signed-in request, from the account ID and authorities of its access token. */
    public static CalendarViewer of(Long userAccountId, Collection<? extends GrantedAuthority> authorities) {
        Set<String> names = AuthorityUtils.authorityListToSet(authorities);
        return new CalendarViewer(userAccountId, names.contains("ROLE_STUDENT"), names.contains("ROLE_FACULTY"),
                names.contains("ROLE_ADMIN"));
    }
}
