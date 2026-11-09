package com.stacc.backend.lms.quiz;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.content.LmsTextFormat;

/** Quiz settings for tests. */
public final class QuizFixtures {

    private QuizFixtures() {
    }

    public static LmsQuizSettings graded(String title) {
        return settings(title, QuizType.GRADED, null, null, null);
    }

    public static LmsQuizSettings practice(String title) {
        return settings(title, QuizType.PRACTICE, null, null, null);
    }

    public static LmsQuizSettings settings(String title, QuizType type, Instant from, Instant due, Instant until) {
        return new LmsQuizSettings(title, "Answer every question.", LmsTextFormat.FORMATTED, type, 20, from, due, until, 2,
                false, false, BigDecimal.ZERO);
    }

    public static LmsQuizSettings withNegative(LmsQuizSettings settings, String percent) {
        return new LmsQuizSettings(settings.title(), settings.instructions(), settings.instructionsFormat(), settings.type(),
                settings.timeLimitMinutes(), settings.availableFrom(), settings.dueAt(), settings.availableUntil(),
                settings.maxAttempts(), settings.shuffleQuestions(), settings.shuffleOptions(), new BigDecimal(percent));
    }
}
