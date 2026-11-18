package com.stacc.backend.support.helpdesk;

public enum HelpdeskTicketPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    public boolean isRequesterChoice() {
        return this != URGENT;
    }
}
