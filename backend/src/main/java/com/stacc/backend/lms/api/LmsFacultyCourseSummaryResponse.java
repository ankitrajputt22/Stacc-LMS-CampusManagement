package com.stacc.backend.lms.api;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/** What a faculty member needs to recognise one of the LMS courses they teach. Nothing else is exposed. */
public record LmsFacultyCourseSummaryResponse(
        @Schema(description = "The ID of the LMS course. It identifies the learning space.", example = "42")
        Long lmsCourseId,

        @Schema(example = "BCS301")
        String courseCode,

        @Schema(example = "Data Structures")
        String courseName,

        @Schema(example = "4.00")
        BigDecimal credits,

        @Schema(description = "Which semester of the program the course is offered in.", example = "3")
        int semesterNumber,

        @Schema(example = "BTECH")
        String programCode,

        @Schema(example = "2026-27")
        String academicSessionCode,

        @Schema(
                description = "DRAFT while the course is being prepared and not yet open to students; "
                        + "PUBLISHED once students can use it.",
                allowableValues = {"DRAFT", "PUBLISHED"},
                example = "PUBLISHED")
        String lmsCourseStatus) {
}
