package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A practice answer to mark (v0.15.4). The question and reference answer come from the student's own practice set on the
 * page; the marks are practice only and nothing is stored.
 */
public record AiEvaluateRequest(
        @NotBlank @Size(max = 1500) String question,
        @NotBlank @Size(max = 2000) String referenceAnswer,
        @NotBlank @Size(max = 4000) String studentAnswer,
        @Schema(description = "From 1 to 10.") @NotNull @Min(1) @Max(10) Integer maxMarks,
        @Schema(description = "PRACTICE (the default), MOCK_EXAM, or VIVA.", nullable = true) Mode mode) {

    public enum Mode { PRACTICE, MOCK_EXAM, VIVA }
}
