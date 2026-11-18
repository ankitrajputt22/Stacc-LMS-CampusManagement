package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiFollowUpTurn(
        @Schema(example = "What does commit do?") @NotBlank @Size(max = 1000) String question,
        @Schema(example = "It makes a transaction's changes durable.") @NotBlank @Size(max = 2000) String answer) {
}
