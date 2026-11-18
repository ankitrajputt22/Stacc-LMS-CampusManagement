package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiAssessmentDraftRequest(
        @Schema(description = "From 1 to 20.", example = "5") @NotNull @Min(1) @Max(20) Integer questionCount,
        @Schema(description = "Generation guidance only.") @NotNull AiDifficulty difficulty,
        @NotNull AiQuestionType questionType,
        @Schema(description = "Optional guidance on focus, at most 500 characters.", example = "Focus on normal forms.")
        @Size(max = 500) String additionalInstruction) {
}
