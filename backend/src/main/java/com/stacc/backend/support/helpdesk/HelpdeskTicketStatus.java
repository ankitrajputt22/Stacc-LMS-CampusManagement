package com.stacc.backend.support.helpdesk;

public enum HelpdeskTicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_ON_REQUESTER,
    RESOLVED,
    CLOSED;

    public boolean canMoveTo(HelpdeskTicketStatus target) {
        return target == this || switch (this) {
            case OPEN -> target == IN_PROGRESS || target == WAITING_ON_REQUESTER || target == RESOLVED || target == CLOSED;
            case IN_PROGRESS -> target == WAITING_ON_REQUESTER || target == RESOLVED || target == CLOSED;
            case WAITING_ON_REQUESTER -> target == IN_PROGRESS || target == RESOLVED || target == CLOSED;
            case RESOLVED -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}
