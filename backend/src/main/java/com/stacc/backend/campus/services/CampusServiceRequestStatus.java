package com.stacc.backend.campus.services;

import com.stacc.backend.approval.ApprovalRequestStatus;

public enum CampusServiceRequestStatus {
    SUBMITTED,
    APPROVED,
    IN_PROGRESS,
    READY,
    REJECTED,
    CANCELLED,
    COMPLETED;

    public boolean isBeingHandled() {
        return this == APPROVED || this == IN_PROGRESS || this == READY;
    }

    public boolean isTerminal() {
        return this == REJECTED || this == CANCELLED || this == COMPLETED;
    }

    public CampusServiceRequestStatus withApproval(ApprovalRequestStatus approval) {
        if (this != SUBMITTED || approval == null) {
            return this;
        }
        return switch (approval) {
            case PENDING -> SUBMITTED;
            case APPROVED -> APPROVED;
            case REJECTED -> REJECTED;
            case CANCELLED -> CANCELLED;
        };
    }

    public ApprovalRequestStatus approvalEquivalent() {
        return switch (this) {
            case APPROVED -> ApprovalRequestStatus.APPROVED;
            case REJECTED -> ApprovalRequestStatus.REJECTED;
            case CANCELLED -> ApprovalRequestStatus.CANCELLED;
            default -> null;
        };
    }
}
