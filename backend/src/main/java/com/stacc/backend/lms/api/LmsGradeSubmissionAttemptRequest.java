package com.stacc.backend.lms.api;

import java.math.BigDecimal;

import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The marks and feedback for one attempt. Which attempt comes from the route; who grades, the maximum, and
 * the time are decided by the server, and anything else in the body is ignored.
 */
public record LmsGradeSubmissionAttemptRequest(
        @Schema(description = "From zero up to the maximum marks, with at most two decimal places.", example = "18")
        @NotNull(message = "Marks are required")
        @DecimalMin(value = "0", message = "Marks cannot be negative")
        @Digits(integer = 6, fraction = LmsAssignment.MARK_DECIMAL_PLACES, message = "Marks can have at most 2 decimal places")
        BigDecimal marksAwarded,

        @Schema(description = "Optional plain text, up to 10,000 characters. Line breaks are kept; HTML is not interpreted.",
                example = "Good solution. Explain question 4 further.")
        @Size(max = LmsSubmissionGrade.MAX_FEEDBACK_LENGTH, message = "Feedback must be at most 10,000 characters")
        String feedback) {
}
