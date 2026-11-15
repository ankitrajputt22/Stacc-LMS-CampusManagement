package com.stacc.backend.lms.ai.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** A suggested model answer to a bank question (v0.14.5), for the teacher only; nothing is saved. */
public record AiModelAnswerResponse(
        @Schema(nullable = true) String answer,
        @Schema(description = "Points a marker should look for.") List<String> keyPoints,
        List<AiCitationResponse> citations,
        boolean insufficientContext) {
}
