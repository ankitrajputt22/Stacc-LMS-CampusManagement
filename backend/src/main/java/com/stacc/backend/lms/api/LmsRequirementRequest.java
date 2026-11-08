package com.stacc.backend.lms.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Turns a learning requirement on or off: a required page, or a module that needs the previous one completed first. */
public record LmsRequirementRequest(
        @Schema(example = "true")
        @NotNull(message = "Say whether it is required")
        Boolean required) {
}
