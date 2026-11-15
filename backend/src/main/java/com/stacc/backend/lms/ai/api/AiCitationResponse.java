package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A published course source an AI result is based on. The title and file name come from Stacc's own records; there
 * is never a storage key, path, or similarity score.
 */
public record AiCitationResponse(
        @Schema(description = "TOPIC, STUDY_MATERIAL, NOTE (the asking student's own note), or, in AI Tools only, TEACHER_DOCUMENT.") String sourceType,
        long sourceId,
        String title,
        @Schema(description = "The study material's file name, or null for a topic.") String fileName,
        @Schema(description = "The PDF page, when known.") Integer pageNumber,
        @Schema(description = "The slide, when known.") Integer slideNumber,
        @Schema(description = "The course page (topic) the source is on, for a link to it: the topic itself, or a study "
                + "material's page. Null for notes and teacher documents (v0.13.9).") Long pageId) {

    /** A source that is on no course page: a note or a teacher document. */
    public AiCitationResponse(String sourceType, long sourceId, String title, String fileName, Integer pageNumber,
            Integer slideNumber) {
        this(sourceType, sourceId, title, fileName, pageNumber, slideNumber, null);
    }
}
