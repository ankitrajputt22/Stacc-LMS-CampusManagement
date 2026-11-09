package com.stacc.backend.academic.result.api;

import java.math.BigDecimal;

import com.stacc.backend.academic.result.CourseResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The official result to record for one enrollment. The student, course, status, recorder, and publication time are
 * never taken from the client: they come from the route, the access token, and the publish action.
 */
public record CourseResultRequest(
        @Schema(description = "Zero or more, at most the maximum marks, with at most two decimal places.", example = "82")
        @NotNull(message = "Marks obtained are required")
        @DecimalMin(value = "0", message = "Marks obtained cannot be negative")
        @Digits(integer = 6, fraction = CourseResult.MARK_DECIMAL_PLACES,
                message = "Marks must be below 1,000,000 with at most 2 decimal places")
        BigDecimal marksObtained,

        @Schema(description = "The official maximum for this course, greater than zero. Nothing assumes 100.", example = "100")
        @NotNull(message = "Maximum marks are required")
        @DecimalMin(value = "0", inclusive = false, message = "Maximum marks must be greater than zero")
        @Digits(integer = 6, fraction = CourseResult.MARK_DECIMAL_PLACES,
                message = "Marks must be below 1,000,000 with at most 2 decimal places")
        BigDecimal maxMarks,

        @Schema(description = "Optional official grade code, kept as entered apart from spaces. It is never calculated.",
                example = "A")
        @Size(max = CourseResult.MAX_GRADE_CODE_LENGTH, message = "Grade must be at most 20 characters")
        String gradeCode,

        @Schema(description = "Optional plain text. Line breaks are kept; HTML is not interpreted.", example = "Good performance")
        @Size(max = CourseResult.MAX_REMARKS_LENGTH, message = "Remarks must be at most 2,000 characters")
        String remarks) {
}
