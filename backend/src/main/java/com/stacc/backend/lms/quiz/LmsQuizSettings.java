package com.stacc.backend.lms.quiz;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.content.LmsTextFormat;

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
        BigDecimal negativeMarkPercent,
        QuizResultRelease resultRelease,
        boolean showAnswers) {

    public LmsQuizSettings(String title, String instructions, LmsTextFormat instructionsFormat, QuizType type,
            Integer timeLimitMinutes, Instant availableFrom, Instant dueAt, Instant availableUntil, Integer maxAttempts,
            boolean shuffleQuestions, boolean shuffleOptions, BigDecimal negativeMarkPercent) {
        this(title, instructions, instructionsFormat, type, timeLimitMinutes, availableFrom, dueAt, availableUntil, maxAttempts,
                shuffleQuestions, shuffleOptions, negativeMarkPercent, QuizResultRelease.AFTER_MARKING, false);
    }
}
