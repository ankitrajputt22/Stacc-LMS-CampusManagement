package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.lms.assignment.LmsAssignment;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One published assignment as a student opens it. The course code and name come from the official catalog;
 * nothing about the student, the enrollment, or the teachers is included.
 */
public record LmsCourseAssignmentDetailResponse(
        @Schema(example = "9")
        Long id,

        @Schema(description = "The LMS course the assignment belongs to.", example = "42")
        Long lmsCourseId,

        @Schema(example = "BCS301")
        String courseCode,

        @Schema(example = "Data Structures")
        String courseName,

        @Schema(example = "Arrays practice")
        String title,

        @Schema(description = "Plain text with line breaks. It is never HTML.",
                example = "Solve problems 1 to 5 from the arrays worksheet.")
        String instructions,

        @Schema(description = "The deadline, in UTC. It may already have passed.", example = "2026-10-20T18:29:00Z")
        Instant dueAt,

        @Schema(example = "20.00")
        BigDecimal maxMarks,

        @Schema(description = "When it was first published.", example = "2026-10-14T09:30:00Z")
        Instant publishedAt) {

    static LmsCourseAssignmentDetailResponse of(LmsAssignment assignment) {
        Course course = assignment.getLmsCourse().getCourseOffering().getCourse();
        return new LmsCourseAssignmentDetailResponse(
                assignment.getId(),
                assignment.getLmsCourse().getId(),
                course.getCode(),
                course.getName(),
                assignment.getTitle(),
                assignment.getInstructions(),
                assignment.getDueAt(),
                assignment.getMaxMarks(),
                assignment.getPublishedAt());
    }
}
