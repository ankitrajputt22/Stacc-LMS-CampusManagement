package com.stacc.backend.lms.ai.api;

import com.stacc.backend.lms.ai.teacher.AiTeacherDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A teacher-only document's title and plain text. */
public record AiTeacherDocumentRequest(
        @Schema(example = "Exam focus for unit 2") @NotBlank @Size(max = AiTeacherDocument.MAX_TITLE_LENGTH) String title,
        @Schema(description = "Plain text; Markdown headings split it into sections for AI.")
        @NotBlank @Size(max = AiTeacherDocument.MAX_CONTENT_LENGTH) String content) {
}
