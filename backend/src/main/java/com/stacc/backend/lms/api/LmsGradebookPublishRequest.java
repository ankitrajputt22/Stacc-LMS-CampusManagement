package com.stacc.backend.lms.api;

import com.stacc.backend.lms.gradebook.GradeItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** The gradebook column to publish: its type and the assignment, quiz, or manual item ID. */
public record LmsGradebookPublishRequest(
        @NotNull(message = "Column type is required")
        GradeItemType type,
        @Schema(example = "12")
        @NotNull(message = "Column is required")
        Long sourceId) {
}
