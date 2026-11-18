package com.stacc.backend.lms.quiz.attempt;

import java.time.Instant;

public enum QuizResultStatus {
    NOT_HANDED_IN,
    WAITING_FOR_MARKING,
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
