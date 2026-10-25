package com.stacc.backend.support.helpdesk;

/** The kinds of change a ticket's history records. */
public enum HelpdeskTicketHistoryAction {
    CREATED,
    MESSAGE_ADDED,
    STATUS_CHANGED,
    PRIORITY_CHANGED,
    ASSIGNED,
    UNASSIGNED
}
