package com.stacc.backend.lms.api;

import com.stacc.backend.lms.content.LmsStudyMaterial;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The title and description of a study material. The file itself cannot be changed: archive it and upload a new one. */
public record LmsMaterialRequest(
        @Schema(example = "Lecture notes")
        @NotBlank(message = "Title is required")
        @Size(max = LmsStudyMaterial.MAX_TITLE_LENGTH, message = "Title must be at most " + LmsStudyMaterial.MAX_TITLE_LENGTH + " characters")
        String title,

        @Schema(description = "Optional plain text. HTML is not interpreted.", example = "Read before the first class.")
        @Size(max = LmsStudyMaterial.MAX_DESCRIPTION_LENGTH, message = "Description must be at most " + LmsStudyMaterial.MAX_DESCRIPTION_LENGTH + " characters")
        String description) {
}
