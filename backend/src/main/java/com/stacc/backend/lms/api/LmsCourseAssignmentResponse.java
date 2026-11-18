package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.assignment.LmsAssignment;
import io.swagger.v3.oas.annotations.media.Schema;

public record LmsCourseAssignmentResponse(
        @Schema(example = "9")
        Long id,

        @Schema(example = "Arrays practice")
        String title,

        @Schema(description = "The deadline, in UTC. It may already have passed.", example = "2026-10-20T18:29:00Z")
        Instant dueAt,

        @Schema(example = "20.00")
        BigDecimal maxMarks) {

    static LmsCourseAssignmentResponse of(LmsAssignment assignment) {
        return new LmsCourseAssignmentResponse(
                assignment.getId(), assignment.getTitle(), assignment.getDueAt(), assignment.getMaxMarks());
    }
}
