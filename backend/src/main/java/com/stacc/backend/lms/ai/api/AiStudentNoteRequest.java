package com.stacc.backend.lms.ai.api;

import com.stacc.backend.lms.ai.notes.AiStudentNote;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiStudentNoteRequest(
        @Schema(example = "Normal forms summary") @NotBlank @Size(max = AiStudentNote.MAX_TITLE_LENGTH) String title,
        @Schema(description = "Plain text, up to 10,000 characters.")
        @NotBlank @Size(max = AiStudentNote.MAX_CONTENT_LENGTH) String content) {
}
