package com.stacc.backend.academic.result.api;

import java.math.BigDecimal;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import io.swagger.v3.oas.annotations.media.Schema;

/** A course offering the faculty member teaches, as the results pages show it. Nothing else is exposed. */
public record ResultCourseOfferingResponse(
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

    static ResultCourseOfferingResponse of(CourseOffering courseOffering) {
        Course course = courseOffering.getCourse();
        Semester semester = courseOffering.getSemester();
        return new ResultCourseOfferingResponse(
                courseOffering.getId(),
                course.getCode(),
                course.getName(),
                course.getCredits(),
                semester.getSemesterNumber(),
                semester.getProgram().getCode(),
                semester.getAcademicSession().getCode());
    }
}
