package com.stacc.backend.lms.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Whether a study material is kept out of the course's AI knowledge. */
public record LmsMaterialAiExclusionRequest(
        @Schema(description = "True keeps the file out of AI knowledge; false includes it again.", example = "true")
        @NotNull
        Boolean excluded) {
}
