package com.stacc.backend.lms.api;

import com.stacc.backend.lms.outcome.LmsCourseOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The description of a course outcome. The code is given by the server. */
public record LmsOutcomeRequest(
        @Schema(description = "Plain text.", example = "Analyse the cost of common data structures.")
        @NotBlank(message = "Description is required")
        @Size(max = LmsCourseOutcome.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 500 characters")
        String description) {
}
