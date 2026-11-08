package com.stacc.backend.lms.quiz.attempt;

import java.time.Instant;

/** Whether a student may see an attempt's result yet, and if not, why. Worked out on every read. */
public enum QuizResultStatus {
    /** Still open: nothing to mark yet. */
    NOT_HANDED_IN,
    /** At least one answer waits for a teacher. */
    WAITING_FOR_MARKING,
    /** Fully marked, but the quiz shows results only after its due date. */
    WAITING_FOR_RELEASE,
    RELEASED;

    public static QuizResultStatus of(LmsQuizAttempt attempt, QuizScoring.AttemptScore score, Instant now) {
        if (attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS) {
            return NOT_HANDED_IN;
        }
        if (!score.fullyMarked()) {
            return WAITING_FOR_MARKING;
        }
        return attempt.getQuiz().resultsReleasedAt(now) ? RELEASED : WAITING_FOR_RELEASE;
    }
}
