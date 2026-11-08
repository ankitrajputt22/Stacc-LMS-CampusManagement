package com.stacc.backend.lms.quiz;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.content.LmsTextFormat;

/**
 * Everything about a quiz that faculty choose, apart from its questions. Empty optional values mean: no instructions,
 * no time limit, open from publication, no due date, never closing, and unlimited attempts.
 */
public record LmsQuizSettings(
        String title,
        String instructions,
        LmsTextFormat instructionsFormat,
        QuizType type,
        Integer timeLimitMinutes,
        Instant availableFrom,
        Instant dueAt,
        Instant availableUntil,
        Integer maxAttempts,
        boolean shuffleQuestions,
        boolean shuffleOptions,
        BigDecimal negativeMarkPercent) {
}
