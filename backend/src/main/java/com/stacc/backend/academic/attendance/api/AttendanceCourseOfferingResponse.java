package com.stacc.backend.academic.attendance.api;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/** A course offering the faculty member teaches, as the attendance pages show it. Nothing else is exposed. */
public record AttendanceCourseOfferingResponse(
        @Schema(description = "The ID of the course offering: one course in one semester.", example = "17")
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
        String academicSessionCode) {
}
