package com.stacc.backend.support.helpdesk;

/** How urgent a ticket is. Requesters choose up to HIGH; only the support team sets URGENT. */
public enum HelpdeskTicketPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    /** Whether a requester may choose this priority when raising a ticket. */
    public boolean isRequesterChoice() {
        return this != URGENT;
    }
}
