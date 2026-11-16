package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** What to summarise and how (v0.15.2). */
public record AiSummaryRequest(
        @NotNull AiSummaryScope scope,
        @Schema(description = "The topic, module, or study material ID.", example = "31") @NotNull @Positive Long id,
        @Schema(description = "BRIEF when omitted.", nullable = true) AiSummaryStyle style) {
}
