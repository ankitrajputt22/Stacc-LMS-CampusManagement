package com.stacc.backend.lms.api;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import com.stacc.backend.lms.course.LmsCourseHomeView;

/**
 * What a student's course page shows about one LMS course. Everything comes from the official
 * academic records; nothing about the student, the enrollment, or the membership is included.
 */
public record LmsCourseDetailResponse(
        @Schema(description = "The ID of the LMS course. It identifies the learning space.", example = "42")
        Long lmsCourseId,

        @Schema(example = "BCS301")
        String courseCode,

        @Schema(example = "Data Structures")
        String courseName,

        @Schema(example = "4.00")
        BigDecimal credits,

        @Schema(description = "The department that owns the course.", example = "CSE")
        String departmentCode,

        @Schema(example = "Computer Science and Engineering")
        String departmentName,

        @Schema(example = "BTECH")
        String programCode,

        @Schema(example = "Bachelor of Technology")
        String programName,

        @Schema(description = "Which semester of the program the course is offered in.", example = "3")
        int semesterNumber,

        @Schema(example = "2026-27")
        String academicSessionCode,
        @Schema(description = "The section, or null when the offering is for the whole semester.", example = "A")
        String sectionCode,
        @ArraySchema(arraySchema = @Schema(description = "The names of the active faculty teaching the offering."))
        List<String> faculty,
        @Schema(description = "What the course home shows.", allowableValues = {"OVERVIEW", "MODULES", "SYLLABUS"})
        LmsCourseHomeView homeView,
        @Schema(description = "The course's welcome message in formatted text, or null. Never HTML.")
        String welcomeMessage) {
}
