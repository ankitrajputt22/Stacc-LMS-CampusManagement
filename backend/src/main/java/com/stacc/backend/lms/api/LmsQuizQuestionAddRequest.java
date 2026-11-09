package com.stacc.backend.lms.api;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** A bank question to add to a draft quiz, with its marks in this quiz. */
public record LmsQuizQuestionAddRequest(
        @Schema(description = "An active question of the same course's bank.", example = "41")
        @NotNull(message = "Choose a question")
        Long questionId,

        @Schema(description = "Optional; the question's bank marks when left out.", example = "2")
        @DecimalMin(value = "0", inclusive = false, message = "Marks must be greater than zero")
        @Digits(integer = 6, fraction = 2, message = "Marks must be below 1,000,000 with at most 2 decimal places")
        BigDecimal marks) {
}
