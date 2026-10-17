package com.stacc.backend.academic.result.api;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** A course offering the faculty member teaches, with every enrollment and its result, in student ID order. */
public record CourseResultRosterResponse(
        @Schema(example = "17")
        Long courseOfferingId,

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

        @Schema(description = "Every official enrollment of the offering, withdrawn ones included.")
        List<Student> students) {

    public record Student(
            @Schema(description = "The enrollment row being managed. It is checked against the offering on every change.",
                    example = "40")
            Long courseEnrollmentId,

            @Schema(description = "The student's college ID. There is no name to show.", example = "2408400100011")
            String studentLoginId,

            @Schema(allowableValues = {"ENROLLED", "COMPLETED", "WITHDRAWN"}, example = "ENROLLED")
            String courseEnrollmentStatus,

            @Schema(description = "The result, or null when none has been saved.")
            FacultyResultResponse result) {
    }
}
