package com.stacc.backend.lms.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionService;
import io.swagger.v3.oas.annotations.media.Schema;

/** One of the student's own attempts. Nothing about the enrollment, the account, or the storage is included. */
public record LmsSubmissionAttemptResponse(
        @Schema(example = "12")
        Long attemptId,

        @Schema(description = "1 for the first attempt, then one more each time.", example = "2")
        int attemptNumber,

        @Schema(description = "Plain text with line breaks, or null when only files were sent. It is never HTML.",
                example = "My answers are in the attached file.")
        String textContent,

        @Schema(description = "When the server accepted the attempt.", example = "2026-10-20T17:04:00Z")
        Instant submittedAt,

        @Schema(description = "Whether the attempt came after the deadline that applied when it was made. It does not "
                + "change if the deadline is moved later.", example = "false")
        boolean late,

        List<LmsSubmissionAttachmentResponse> attachments) {

    static LmsSubmissionAttemptResponse of(LmsAssignmentSubmissionService.SubmittedAttempt submitted) {
        return new LmsSubmissionAttemptResponse(
                submitted.attempt().getId(),
                submitted.attempt().getAttemptNumber(),
                submitted.attempt().getTextContent(),
                submitted.attempt().getSubmittedAt(),
                submitted.attempt().isLate(),
                submitted.attachments().stream().map(LmsSubmissionAttachmentResponse::of).toList());
    }
}
