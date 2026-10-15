package com.stacc.backend.lms.api;

import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import io.swagger.v3.oas.annotations.media.Schema;

/** One study material as faculty manage it. Where the file is stored is never sent. */
public record LmsFacultyMaterialResponse(
        @Schema(example = "7")
        Long id,

        @Schema(example = "Lecture notes")
        String title,

        @Schema(description = "Plain text, or null.", example = "Read before the first class.")
        String description,

        @Schema(example = "Lecture notes.pdf")
        String originalFileName,

        @Schema(example = "application/pdf")
        String contentType,

        @Schema(example = "1887436")
        long sizeBytes,

        @Schema(description = "The material's place in its topic, starting at 1.", example = "1")
        int position,

        @Schema(description = "DRAFT is hidden from students. PUBLISHED is shown once its topic and module are published.")
        LmsContentStatus status) {

    static LmsFacultyMaterialResponse of(LmsStudyMaterial material) {
        return new LmsFacultyMaterialResponse(material.getId(), material.getTitle(), material.getDescription(),
                material.getOriginalFileName(), material.getContentType(), material.getSizeBytes(),
                material.getPosition(), material.getStatus());
    }
}
