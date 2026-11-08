package com.stacc.backend.lms.quiz.attempt;

/** How an attempt looks at one moment: its stored status, with an open attempt past its deadline shown as time up. */
public enum QuizAttemptState {
    IN_PROGRESS,
    /** Still stored as in progress, but its deadline has passed: no more answers are taken. */
    TIME_UP,
    SUBMITTED,
    EXPIRED
}
