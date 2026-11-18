package com.stacc.backend.lms.ai.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record AiModelAnswerResponse(
        @Schema(nullable = true) String answer,
        @Schema(description = "Points a marker should look for.") List<String> keyPoints,
        List<AiCitationResponse> citations,
        boolean insufficientContext) {
}
