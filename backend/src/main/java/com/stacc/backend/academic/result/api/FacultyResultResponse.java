package com.stacc.backend.academic.result.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.academic.result.CourseResult;
import io.swagger.v3.oas.annotations.media.Schema;

/** One official result as faculty manage it. Who recorded it is not sent. */
public record FacultyResultResponse(
        @Schema(example = "31")
        Long resultId,

        @Schema(example = "82.00")
        BigDecimal marksObtained,

        @Schema(example = "100.00")
        BigDecimal maxMarks,

        @Schema(description = "The official grade code as entered, or null.", example = "A")
        String gradeCode,

        @Schema(description = "Plain text, or null.", example = "Good performance")
        String remarks,

        @Schema(allowableValues = {"DRAFT", "PUBLISHED"}, example = "DRAFT")
        String status,

        @Schema(description = "When it was first published, or null for a draft. Corrections never change it.")
        Instant publishedAt,

        @Schema(description = "When it was last saved.")
        Instant updatedAt) {

    static FacultyResultResponse of(CourseResult result) {
        return result == null ? null : new FacultyResultResponse(result.getId(), result.getMarksObtained(),
                result.getMaxMarks(), result.getGradeCode(), result.getRemarks(), result.getStatus().name(),
                result.getPublishedAt(), result.getUpdatedAt());
    }
}
