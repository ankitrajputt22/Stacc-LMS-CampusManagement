package com.stacc.backend.support.helpdesk.api;

/**
 * The route rules of the helpdesk, in one place. They let a caller ask; the helpdesk access rules still decide per
 * ticket. Admins get no pass beyond the explicit "all" permissions.
 */
final class HelpdeskAccess {

    private static final String ANY_ROLE = "hasAnyRole('STUDENT', 'FACULTY', 'ADMIN')";

    static final String CREATE = ANY_ROLE + " and hasAuthority('HELPDESK_TICKET_CREATE')";
    static final String VIEW_OWN = ANY_ROLE + " and hasAuthority('HELPDESK_TICKET_VIEW_OWN')";
    static final String VIEW_ONE = ANY_ROLE + " and (hasAuthority('HELPDESK_TICKET_VIEW_OWN')"
            + " or (hasRole('ADMIN') and hasAuthority('HELPDESK_TICKET_VIEW_ALL')))";
    static final String REPLY = ANY_ROLE + " and (hasAuthority('HELPDESK_TICKET_REPLY_OWN')"
            + " or (hasRole('ADMIN') and hasAuthority('HELPDESK_TICKET_REPLY_ALL')))";
    static final String VIEW_ALL = "hasRole('ADMIN') and hasAuthority('HELPDESK_TICKET_VIEW_ALL')";
    static final String MANAGE = "hasRole('ADMIN') and hasAuthority('HELPDESK_TICKET_MANAGE')";

    private HelpdeskAccess() {
    }
}
