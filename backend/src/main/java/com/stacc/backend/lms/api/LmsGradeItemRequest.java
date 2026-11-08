package com.stacc.backend.lms.api;

import java.math.BigDecimal;

import com.stacc.backend.lms.gradebook.LmsGradeItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A manual gradebook item, such as a class test on paper. */
public record LmsGradeItemRequest(
        @Schema(example = "Class test 1")
        @NotBlank(message = "Title is required")
        @Size(max = LmsGradeItem.MAX_TITLE_LENGTH, message = "Title must be at most 200 characters")
        String title,
        @Schema(example = "25")
        @NotNull(message = "Maximum marks are required")
        @DecimalMin(value = "0", inclusive = false, message = "Maximum marks must be greater than zero")
        @Digits(integer = 6, fraction = 2, message = "Maximum marks must be below 1,000,000 with at most 2 decimal places")
        BigDecimal maxMarks) {
}
