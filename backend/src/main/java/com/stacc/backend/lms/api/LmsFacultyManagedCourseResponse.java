package com.stacc.backend.lms.api;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One course offering a faculty member officially teaches, with its LMS course if it has one. Nothing about the
 * teaching assignment, the faculty profile, or other teachers is exposed.
 */
public record LmsFacultyManagedCourseResponse(
        @Schema(example = "31")
        Long courseOfferingId,

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

        @Schema(description = "The offering's LMS course, or null when none has been created yet.", example = "42",
                nullable = true)
        Long lmsCourseId,

        @Schema(description = "DRAFT while the course is prepared, PUBLISHED once students with official enrollment "
                + "can use it, ARCHIVED once it is kept only as history. Null when the offering has no LMS course yet; "
                + "that is not a stored status.", allowableValues = {"DRAFT", "PUBLISHED", "ARCHIVED"},
                example = "DRAFT", nullable = true)
        String lmsCourseStatus) {
}
