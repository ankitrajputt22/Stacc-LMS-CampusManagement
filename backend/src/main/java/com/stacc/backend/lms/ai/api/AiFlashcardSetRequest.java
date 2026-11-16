package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** What to make flashcards from (v0.15.3). */
public record AiFlashcardSetRequest(
        @NotNull Scope scope,
        @Schema(description = "The topic, module, or file ID; omitted for COURSE.", nullable = true) @Positive Long id,
        @Schema(description = "From 3 to 30.", example = "10") @NotNull @Min(3) @Max(30) Integer cardCount,
        @Schema(description = "The set's title; the page's or file's title when omitted.", nullable = true) @Size(max = 200) String title) {

    public enum Scope { COURSE, TOPIC, MODULE, DOCUMENT }
}
