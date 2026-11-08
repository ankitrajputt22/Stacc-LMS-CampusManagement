package com.stacc.backend.lms.api;

import java.math.BigDecimal;

import com.stacc.backend.lms.gradebook.LmsGradeEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A student's marks for a manual item, with optional plain-text feedback. */
public record LmsGradeEntryRequest(
        @Schema(example = "18.5")
        @NotNull(message = "Marks are required")
        @DecimalMin(value = "0", message = "Marks cannot be negative")
        @Digits(integer = 6, fraction = 2, message = "Marks can have at most 2 decimal places")
        BigDecimal marks,
        @Size(max = LmsGradeEntry.MAX_FEEDBACK_LENGTH, message = "Feedback must be at most 10,000 characters")
        String feedback) {
}
