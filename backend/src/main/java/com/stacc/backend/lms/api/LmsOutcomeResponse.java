package com.stacc.backend.lms.api;

import com.stacc.backend.lms.outcome.LmsCourseOutcome;
import com.stacc.backend.lms.outcome.LmsOutcomeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** One course learning outcome. Plain text, never HTML. */
public record LmsOutcomeResponse(
        @Schema(example = "12")
        Long id,
        @Schema(description = "Given by the server and never reused.", example = "CO2")
        String code,
        @Schema(example = "Analyse the cost of common data structures.")
        String description,
        @Schema(description = "ARCHIVED outcomes stay on what they were aligned to but take no new alignments.")
        LmsOutcomeStatus status) {

    static LmsOutcomeResponse of(LmsCourseOutcome outcome) {
        return new LmsOutcomeResponse(outcome.getId(), outcome.getCode(), outcome.getDescription(), outcome.getStatus());
    }
}
