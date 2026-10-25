package com.stacc.backend.support.helpdesk;

import java.util.Collection;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Who is acting on the helpdesk: the account in the access token, whether it holds the admin role, and which helpdesk
 * permissions it has. It is read from the token only, never from anything the client sends.
 */
public record HelpdeskActor(Long accountId, boolean admin, boolean viewOwn, boolean replyOwn, boolean viewAll,
        boolean replyAll, boolean manage) {

    public static final String CREATE = "HELPDESK_TICKET_CREATE";
    public static final String VIEW_OWN = "HELPDESK_TICKET_VIEW_OWN";
    public static final String REPLY_OWN = "HELPDESK_TICKET_REPLY_OWN";
    public static final String VIEW_ALL = "HELPDESK_TICKET_VIEW_ALL";
    public static final String MANAGE = "HELPDESK_TICKET_MANAGE";
    public static final String REPLY_ALL = "HELPDESK_TICKET_REPLY_ALL";

    public static HelpdeskActor of(Long accountId, Collection<? extends GrantedAuthority> authorities) {
        Set<String> names = AuthorityUtils.authorityListToSet(authorities);
        return new HelpdeskActor(accountId, names.contains("ROLE_ADMIN"), names.contains(VIEW_OWN),
                names.contains(REPLY_OWN), names.contains(VIEW_ALL), names.contains(REPLY_ALL), names.contains(MANAGE));
    }
}
