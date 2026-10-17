package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** One student's submission for an assignment, with every attempt, newest first, for faculty review. */
public record LmsFacultySubmissionDetailResponse(
        @Schema(example = "9")
        Long assignmentId,

        @Schema(example = "Arrays practice")
        String title,

        @Schema(description = "The assignment's maximum marks now. A first grade is out of this.", example = "20.00")
        BigDecimal maxMarks,

        @Schema(description = "The assignment's deadline now.", example = "2026-10-20T18:29:00Z")
        Instant dueAt,

        @Schema(description = "The student's college ID, the login ID of their account.", example = "2408400100011")
        String studentLoginId,

        @Schema(example = "12")
        Long submissionId,

        @Schema(description = "Newest first, by attempt number.")
        List<LmsFacultySubmissionAttemptResponse> attempts) {
}
