package com.stacc.backend.lms.ai.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record AiSummaryResponse(
        AiSummaryScope scope,
        long id,
        @Schema(description = "The page's or file's title; null for a module, whose title the page already shows.", nullable = true)
        String title,
        AiSummaryStyle style,
        @Schema(nullable = true) String summary,
        List<String> keyPoints,
        boolean insufficientContext,
        List<AiCitationResponse> citations) {
}
