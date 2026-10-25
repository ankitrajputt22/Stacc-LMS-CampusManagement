package com.stacc.backend.support.helpdesk;

/** Where a ticket is in its life. It only moves forward; nothing reopens a ticket. */
public enum HelpdeskTicketStatus {
    /** New, waiting for the support team. */
    OPEN,
    /** The support team is working on it. */
    IN_PROGRESS,
    /** A resolution has been given. Messages may still be added until it is closed. */
    RESOLVED,
    /** Finished. The ticket is read-only. */
    CLOSED;

    /** Whether a ticket may move from this status to the target. The same status is always allowed, and changes nothing. */
    public boolean canMoveTo(HelpdeskTicketStatus target) {
        return target == this || switch (this) {
            case OPEN -> target == IN_PROGRESS || target == RESOLVED || target == CLOSED;
            case IN_PROGRESS -> target == RESOLVED || target == CLOSED;
            case RESOLVED -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}
