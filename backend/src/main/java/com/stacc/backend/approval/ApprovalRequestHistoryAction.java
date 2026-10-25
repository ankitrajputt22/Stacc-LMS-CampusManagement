package com.stacc.backend.approval;

/** What an entry of an approval request's history records. */
public enum ApprovalRequestHistoryAction {
    REQUEST_CREATED,
    STEP_ACTIVATED,
    STEP_APPROVED,
    STEP_REJECTED,
    REQUEST_APPROVED,
    REQUEST_REJECTED,
    REQUEST_CANCELLED
}
