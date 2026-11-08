package com.stacc.backend.lms.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The new order of a draft quiz's questions: every question of the quiz, each exactly once. */
public record LmsQuizQuestionOrderRequest(
        @ArraySchema(arraySchema = @Schema(description = "Quiz question IDs in their new order.", example = "[7, 5, 6]"))
        @NotNull(message = "Question IDs are required")
        @Size(max = 100, message = "Too many question IDs")
        List<@NotNull(message = "Question IDs cannot be empty") Long> quizQuestionIds) {
}
