package com.stacc.backend.lms.api;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** The marks a question carries in one draft quiz. */
public record LmsQuizQuestionMarksRequest(
        @Schema(example = "2.5")
        @NotNull(message = "Marks are required")
        @DecimalMin(value = "0", inclusive = false, message = "Marks must be greater than zero")
        @Digits(integer = 6, fraction = 2, message = "Marks must be below 1,000,000 with at most 2 decimal places")
        BigDecimal marks) {
}
