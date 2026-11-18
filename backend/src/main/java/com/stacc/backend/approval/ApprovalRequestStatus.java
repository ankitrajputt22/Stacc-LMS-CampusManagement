package com.stacc.backend.approval;

public enum ApprovalRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
