package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** One student's submission for an assignment, summed up by its latest attempt, for faculty. */
public record LmsFacultySubmissionSummaryResponse(
        @Schema(example = "12")
        Long submissionId,

        @Schema(description = "The student's college ID, the login ID of their account.", example = "2408400100011")
        String studentLoginId,

        @Schema(example = "2")
        int latestAttemptNumber,

        @Schema(example = "2")
        long attemptCount,

        @Schema(example = "2026-10-20T17:04:00Z")
        Instant latestSubmittedAt,

        @Schema(description = "Whether the latest attempt came after the deadline that applied when it was made.")
        boolean latestLate,

        @Schema(description = "Whether the latest attempt has a grade. An older attempt's grade does not count.")
        boolean latestGraded,

        @Schema(description = "The latest attempt's marks, or null while it is not graded.", example = "18.00")
        BigDecimal latestMarksAwarded,

        @Schema(description = "The maximum the latest attempt was graded out of, or null while it is not graded.",
                example = "20.00")
        BigDecimal latestMaxMarksSnapshot) {
}
