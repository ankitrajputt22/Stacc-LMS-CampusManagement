package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.content.LmsContentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** One assignment in a faculty member's list across all the courses they teach. */
public record LmsFacultyAssignmentOverviewResponse(
        @Schema(description = "The LMS course the assignment belongs to.", example = "42")
        Long lmsCourseId,

        @Schema(example = "BCS301")
        String courseCode,

        @Schema(example = "Data Structures")
        String courseName,

        @Schema(example = "9")
        Long assignmentId,

        @Schema(example = "Arrays practice")
        String title,

        @Schema(description = "The deadline, in UTC.", example = "2026-10-20T18:29:00Z")
        Instant dueAt,

        @Schema(example = "20.00")
        BigDecimal maxMarks,

        @Schema(description = "DRAFT or PUBLISHED. Archived assignments are not listed.",
                allowableValues = {"DRAFT", "PUBLISHED"})
        LmsContentStatus status) {
}
