package com.stacc.backend.support.helpdesk.api;

final class HelpdeskAccess {

    private static final String ANY_ROLE = "hasAnyRole('STUDENT', 'FACULTY', 'ADMIN')";
    static final String TEAM = "hasAnyRole('ADMIN', 'MAINTENANCE', 'WARDEN', 'LIBRARIAN') and hasAuthority('HELPDESK_TEAM_WORK')";

    static final String CREATE = ANY_ROLE + " and hasAuthority('HELPDESK_TICKET_CREATE')";
    static final String VIEW_OWN = ANY_ROLE + " and hasAuthority('HELPDESK_TICKET_VIEW_OWN')";
    static final String VIEW_ONE = "(" + ANY_ROLE + " and (hasAuthority('HELPDESK_TICKET_VIEW_OWN')"
            + " or (hasRole('ADMIN') and hasAuthority('HELPDESK_TICKET_VIEW_ALL')))) or (" + TEAM + ")";
    static final String REPLY = "(" + ANY_ROLE + " and (hasAuthority('HELPDESK_TICKET_REPLY_OWN')"
            + " or (hasRole('ADMIN') and hasAuthority('HELPDESK_TICKET_REPLY_ALL')))) or (" + TEAM + ")";
    static final String VIEW_ALL = "hasRole('ADMIN') and hasAuthority('HELPDESK_TICKET_VIEW_ALL')";
    static final String MANAGE = "hasRole('ADMIN') and hasAuthority('HELPDESK_TICKET_MANAGE')";
    static final String WORK = "(" + MANAGE + ") or (" + TEAM + ")";
    static final String NOTE = "(" + VIEW_ALL + ") or (" + TEAM + ")";

    private HelpdeskAccess() {
    }
}
