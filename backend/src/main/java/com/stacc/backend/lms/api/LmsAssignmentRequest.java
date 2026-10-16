package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.assignment.LmsAssignment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The definition of an assignment, for adding or editing one. The course, status, and publication time
 * are not part of it: they come from the route and the publish and archive actions.
 */
public record LmsAssignmentRequest(
        @Schema(example = "Arrays practice")
        @NotBlank(message = "Title is required")
        @Size(max = LmsAssignment.MAX_TITLE_LENGTH, message = "Title must be at most " + LmsAssignment.MAX_TITLE_LENGTH + " characters")
        String title,

        @Schema(description = "Plain text. Line breaks are kept; HTML is not interpreted.",
                example = "Solve problems 1 to 5 from the arrays worksheet.")
        @NotBlank(message = "Instructions are required")
        @Size(max = LmsAssignment.MAX_INSTRUCTIONS_LENGTH, message = "Instructions must be at most 20,000 characters")
        String instructions,

        @Schema(description = "The deadline, as an ISO-8601 moment with a time zone, such as 2026-10-20T18:29:00Z. "
                + "A date and time without a zone is refused, and the year must be between 2000 and 9999.", example = "2026-10-20T18:29:00Z")
        @NotNull(message = "Due date is required")
        @SupportedDueAt
        Instant dueAt,

        @Schema(description = "Greater than zero, with at most two decimal places.", example = "20")
        @NotNull(message = "Maximum marks are required")
        @DecimalMin(value = "0", inclusive = false, message = "Maximum marks must be greater than zero")
        @Digits(integer = 6, fraction = LmsAssignment.MARK_DECIMAL_PLACES,
                message = "Maximum marks must be below 1,000,000 with at most 2 decimal places")
        BigDecimal maxMarks) {
}
