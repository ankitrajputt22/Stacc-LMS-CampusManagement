package com.stacc.backend.lms.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record LmsRequirementRequest(
        @Schema(example = "true")
        @NotNull(message = "Say whether it is required")
        Boolean required) {
}
