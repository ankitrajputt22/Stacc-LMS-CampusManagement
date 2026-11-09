package com.stacc.backend.lms.quiz.attempt;

/** What is stored about an attempt. Whether an open attempt has run out of time is worked out, never stored. */
public enum QuizAttemptStatus {
    /** Started and not yet handed in. */
    IN_PROGRESS,
    /** Handed in by the student before its deadline. */
    SUBMITTED,
    /** Closed after its deadline passed; the answers saved before the deadline count. */
    EXPIRED
}
