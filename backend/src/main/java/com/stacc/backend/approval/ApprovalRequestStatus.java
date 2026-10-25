package com.stacc.backend.approval;

/** Where an approval request is. Only PENDING requests change; the other three are final. */
public enum ApprovalRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
