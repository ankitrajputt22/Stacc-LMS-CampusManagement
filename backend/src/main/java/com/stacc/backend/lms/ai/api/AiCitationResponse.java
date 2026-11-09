package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A published course source an AI result is based on. The title and file name come from Stacc's own records; there
 * is never a storage key, path, or similarity score.
 */
public record AiCitationResponse(
        @Schema(description = "TOPIC or STUDY_MATERIAL.") String sourceType,
        long sourceId,
        String title,
        @Schema(description = "The study material's file name, or null for a topic.") String fileName,
        @Schema(description = "The PDF page, when known.") Integer pageNumber,
        @Schema(description = "The slide, when known.") Integer slideNumber) {
}
