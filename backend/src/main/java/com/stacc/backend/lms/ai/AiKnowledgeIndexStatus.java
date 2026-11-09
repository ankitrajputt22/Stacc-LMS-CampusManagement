package com.stacc.backend.lms.ai;

/**
 * The state of the latest AI knowledge refresh of an LMS course. There is no row, and so no status, for a course that
 * was never indexed; whether the knowledge is stale is computed from the content fingerprint, never stored.
 */
public enum AiKnowledgeIndexStatus {
    /** A refresh is running. A previous successful index, if any, still answers while it runs. */
    BUILDING,
    /** The latest refresh succeeded. */
    READY,
    /** The latest refresh failed. The previous index files are kept, but students cannot use them until a refresh succeeds. */
    FAILED
}
