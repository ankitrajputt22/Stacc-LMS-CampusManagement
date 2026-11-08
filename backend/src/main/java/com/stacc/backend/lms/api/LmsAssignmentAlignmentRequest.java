package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.lms.outcome.BloomLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What an assignment assesses: an optional Bloom level and outcomes of its own course. */
public record LmsAssignmentAlignmentRequest(
        @Schema(description = "Or null for none.", allowableValues = {"REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE", "EVALUATE", "CREATE"})
        BloomLevel bloomLevel,
        @Schema(description = "Outcome IDs of the assignment's course; empty for none.")
        @NotNull(message = "Outcomes are required (use an empty list for none)")
        @Size(max = 50, message = "Choose at most 50 outcomes")
        List<@NotNull Long> outcomeIds) {
}
