package com.stacc.backend.lms.ai.api;

import java.time.Instant;

import com.stacc.backend.lms.ai.teacher.AiTeacherDocument;
import io.swagger.v3.oas.annotations.media.Schema;

/** A teacher-only document as faculty manage it. Who wrote it is not sent. */
public record AiTeacherDocumentResponse(
        long id,
        String title,
        String content,
        @Schema(description = "ACTIVE or ARCHIVED.") String status,
        Instant createdAt,
        Instant updatedAt) {

    static AiTeacherDocumentResponse of(AiTeacherDocument document) {
        return new AiTeacherDocumentResponse(document.getId(), document.getTitle(), document.getContent(),
                document.getStatus().name(), document.getCreatedAt(), document.getUpdatedAt());
    }
}
