package com.stacc.backend.lms.api;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** One attempt as faculty review it: the student's work, whether it was late, and its grade if any. */
public record LmsFacultySubmissionAttemptResponse(
        @Schema(example = "31")
        Long attemptId,

        @Schema(example = "2")
        int attemptNumber,

        @Schema(description = "Plain text with line breaks, or null when only files were sent. It is never HTML.")
        String textContent,

        @Schema(example = "2026-10-20T17:04:00Z")
        Instant submittedAt,

        @Schema(description = "The deadline that applied when the attempt was made.", example = "2026-10-20T18:29:00Z")
        Instant dueAtSnapshot,

        boolean late,

        List<LmsSubmissionAttachmentResponse> attachments,

        @Schema(description = "The attempt's grade, or null while it is not graded.")
        LmsSubmissionGradeResponse grade) {
}
