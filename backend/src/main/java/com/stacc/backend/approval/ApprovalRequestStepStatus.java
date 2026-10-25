package com.stacc.backend.approval;

/** Where one step of an approval request is. */
public enum ApprovalRequestStepStatus {
    /** An earlier step has not been decided yet. */
    WAITING,
    /** The step that can be decided now. A pending request has exactly one. */
    PENDING,
    APPROVED,
    REJECTED,
    /** The request ended before this step was reached or decided. */
    SKIPPED
}
