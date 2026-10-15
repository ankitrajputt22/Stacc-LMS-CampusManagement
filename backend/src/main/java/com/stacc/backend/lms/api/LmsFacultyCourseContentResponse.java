package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** One LMS course a faculty member teaches, with the content they manage. Archived content is left out. */
public record LmsFacultyCourseContentResponse(
        @Schema(example = "42")
        Long lmsCourseId,

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

        @Schema(
                description = "DRAFT while the course is not yet open to students; PUBLISHED once it is.",
                allowableValues = {"DRAFT", "PUBLISHED"},
                example = "PUBLISHED")
        String lmsCourseStatus,

        @ArraySchema(arraySchema = @Schema(description = "The modules that are not archived, in course order."))
        List<LmsFacultyModuleContentResponse> modules) {
}
