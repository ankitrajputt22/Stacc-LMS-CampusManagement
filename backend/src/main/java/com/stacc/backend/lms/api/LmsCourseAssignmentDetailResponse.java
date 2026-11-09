package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.lms.assignment.AssignmentSubmissionWindow;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.content.LmsTextFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import com.stacc.backend.lms.outcome.BloomLevel;

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

        @Schema(description = "The text with line breaks, shown as its format says. It is never HTML.",
                example = "Solve problems 1 to 5 from the arrays worksheet.")
        String instructions,

        @Schema(description = "The deadline, in UTC. It may already have passed.", example = "2026-10-20T18:29:00Z")
        Instant dueAt,

        @Schema(example = "20.00")
        BigDecimal maxMarks,

        @Schema(description = "When it was first published.", example = "2026-10-14T09:30:00Z")
        Instant publishedAt,
        @Schema(description = "How to show the text: PLAIN exactly as written, FORMATTED with the small formatting set. Never HTML.", allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat instructionsFormat,
        @Schema(description = "The Bloom level it works at, or null.")
        BloomLevel bloomLevel,
        @Schema(description = "The course outcomes it assesses, by code.")
        List<LmsOutcomeResponse> outcomes,
        @Schema(description = "The rubric it is graded with, or null.")
        LmsRubricResponse rubric,
        @Schema(description = "When it starts taking work; null means from publication.")
        Instant availableFrom,
        @Schema(description = "After this no work is taken; null means no final closing time.")
        Instant availableUntil,
        @Schema(description = "Whether work handed in after the due date is taken (and marked late).")
        boolean allowLateSubmissions,
        @Schema(description = "Attempts each student may make; null means unlimited.")
        Integer maxAttempts,
        @Schema(description = "NOT_OPEN, OPEN, or CLOSED, by the server's clock now.")
        AssignmentSubmissionWindow submissionWindow) {

    static LmsCourseAssignmentDetailResponse of(LmsAssignment assignment, Instant now) {
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
                assignment.getPublishedAt(),
                assignment.getInstructionsFormat(),
                assignment.getBloomLevel(),
                assignment.getOutcomes().stream().map(LmsOutcomeResponse::of).toList(),
                LmsRubricResponse.ofNullable(assignment.getRubric()),
                assignment.getAvailableFrom(),
                assignment.getAvailableUntil(),
                assignment.isAllowLateSubmissions(),
                assignment.getMaxAttempts(),
                assignment.submissionWindowAt(now));
    }
}
