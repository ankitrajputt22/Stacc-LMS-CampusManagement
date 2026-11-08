package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizSettings;
import com.stacc.backend.lms.quiz.QuizType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The settings of a quiz, for adding or editing one. The course, questions, status, and publication time are not part
 * of it: they come from the route and the question, publish, and archive actions. Times are ISO-8601 moments with a
 * time zone; a date and time without a zone is refused.
 */
public record LmsQuizRequest(
        @Schema(example = "Normalization check")
        @NotBlank(message = "Title is required")
        @Size(max = LmsQuiz.MAX_TITLE_LENGTH, message = "Title must be at most " + LmsQuiz.MAX_TITLE_LENGTH + " characters")
        String title,

        @Schema(description = "Optional. Shown as instructionsFormat says, never as HTML.")
        @Size(max = LmsQuiz.MAX_INSTRUCTIONS_LENGTH, message = "Instructions must be at most 20,000 characters")
        String instructions,

        @Schema(description = "Left out, new instructions are PLAIN and edited ones keep their format.", allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat instructionsFormat,

        @Schema(description = "GRADED, or PRACTICE (only questions offered for practice). Fixed after publishing.")
        @NotNull(message = "Quiz type is required")
        QuizType type,

        @Schema(description = "Minutes from the start of an attempt, 1 to 600; empty means no time limit.", example = "20")
        @Min(value = 1, message = "The time limit must be at least 1 minute")
        @Max(value = LmsQuiz.MAX_TIME_LIMIT_MINUTES, message = "The time limit must be at most 600 minutes")
        Integer timeLimitMinutes,

        @Schema(description = "When students may start; empty means as soon as it is published.", example = "2026-11-20T03:30:00Z")
        Instant availableFrom,

        @Schema(description = "The due date shown to students; empty means none. Between the opening and closing times.",
                example = "2026-11-21T18:29:00Z")
        Instant dueAt,

        @Schema(description = "After this no attempt may start; empty means it never closes.", example = "2026-11-22T18:29:00Z")
        Instant availableUntil,

        @Schema(description = "How many attempts each student may make, 1 to 100; empty means unlimited.", example = "1")
        @Min(value = 1, message = "The attempt limit must be at least 1")
        @Max(value = LmsQuiz.MAX_ATTEMPTS, message = "The attempt limit must be at most 100")
        Integer maxAttempts,

        @Schema(description = "Each attempt gets its own question order. Fixed after publishing.")
        boolean shuffleQuestions,

        @Schema(description = "Each attempt gets its own choice order. Fixed after publishing.")
        boolean shuffleOptions,

        @Schema(description = "The share of a question's marks taken off for a wrong objective answer, 0 to 100; "
                + "unanswered questions lose nothing. Fixed after publishing.", example = "25")
        @DecimalMin(value = "0", message = "Negative marking must be from 0 to 100 percent")
        @DecimalMax(value = "100", message = "Negative marking must be from 0 to 100 percent")
        @Digits(integer = 3, fraction = 2, message = "Negative marking can have at most 2 decimal places")
        BigDecimal negativeMarkPercent) {

    LmsQuizSettings settings() {
        return new LmsQuizSettings(title, instructions, instructionsFormat, type, timeLimitMinutes, availableFrom, dueAt,
                availableUntil, maxAttempts, shuffleQuestions, shuffleOptions, negativeMarkPercent);
    }
}
