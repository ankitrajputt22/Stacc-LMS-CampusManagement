package com.stacc.backend.campus.services;

import com.stacc.backend.approval.ApprovalRequestStatus;

/**
 * The business status of a campus service request. It is not an approval status: when a service needs approval, the
 * approval engine owns that, and a SUBMITTED request takes its effect from the approval request.
 */
public enum CampusServiceRequestStatus {
    /** Waiting for its approval. */
    SUBMITTED,
    /** Accepted for handling: approved, or needing no approval. */
    APPROVED,
    /** Being processed by the campus office. */
    IN_PROGRESS,
    /** Ready for the requester, for example to collect. */
    READY,
    REJECTED,
    CANCELLED,
    COMPLETED;

    /** Accepted and not finished: approved, being processed, or ready. Such a request can be completed. */
    public boolean isBeingHandled() {
        return this == APPROVED || this == IN_PROGRESS || this == READY;
    }

    public boolean isTerminal() {
        return this == REJECTED || this == CANCELLED || this == COMPLETED;
    }

    /**
     * The status a request in this stored status has once its approval's outcome is taken into account: a SUBMITTED
     * request becomes APPROVED, REJECTED, or CANCELLED with its approval; anything else stays as it is.
     */
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

    /** The approval status that turns a SUBMITTED request into this status, if any. */
    public ApprovalRequestStatus approvalEquivalent() {
        return switch (this) {
            case APPROVED -> ApprovalRequestStatus.APPROVED;
            case REJECTED -> ApprovalRequestStatus.REJECTED;
            case CANCELLED -> ApprovalRequestStatus.CANCELLED;
            default -> null;
        };
    }
}
