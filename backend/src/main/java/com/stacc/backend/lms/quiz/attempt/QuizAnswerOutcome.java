package com.stacc.backend.lms.quiz.attempt;

/** How one question of an attempt came out. */
public enum QuizAnswerOutcome {
    CORRECT,
    INCORRECT,
    UNANSWERED,
    /** A descriptive answer that waits for a teacher's marks. */
    NEEDS_MARKING,
    /** A descriptive answer a teacher has marked. */
    MARKED
}
