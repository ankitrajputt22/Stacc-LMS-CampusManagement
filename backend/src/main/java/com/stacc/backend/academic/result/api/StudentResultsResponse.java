package com.stacc.backend.academic.result.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record StudentResultsResponse(List<Result> results) {

    public record Result(
            @Schema(example = "BCS301")
            String courseCode,

            @Schema(example = "Data Structures")
            String courseName,

            @Schema(example = "4.00")
            BigDecimal credits,

            @Schema(example = "3")
            int semesterNumber,

            @Schema(example = "BTECH")
            String programCode,

            @Schema(example = "2026-27")
            String academicSessionCode,

            @Schema(example = "82.00")
            BigDecimal marksObtained,

            @Schema(example = "100.00")
            BigDecimal maxMarks,

            @Schema(description = "The official grade code, or null when none was entered.", example = "A")
            String gradeCode,

            @Schema(description = "Plain text, or null.", example = "Good performance")
            String remarks,

            @Schema(description = "When it was first published.")
            Instant publishedAt) {
    }
}
