package com.stacc.backend.lms.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record LmsSubmissionHistoryResponse(
        @Schema(description = "Newest first, by attempt number. Empty when nothing has been handed in yet.")
        List<LmsSubmissionAttemptResponse> attempts) {
}
