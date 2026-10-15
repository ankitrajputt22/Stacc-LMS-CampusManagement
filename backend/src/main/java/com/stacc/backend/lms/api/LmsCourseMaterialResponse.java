package com.stacc.backend.lms.api;

import com.stacc.backend.lms.content.LmsStudyMaterial;
import io.swagger.v3.oas.annotations.media.Schema;

/** One published study material, as a student sees it. Where the file is stored is never sent. */
public record LmsCourseMaterialResponse(
        @Schema(description = "Used to download the file.", example = "7")
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
        long sizeBytes) {

    static LmsCourseMaterialResponse of(LmsStudyMaterial material) {
        return new LmsCourseMaterialResponse(material.getId(), material.getTitle(), material.getDescription(),
                material.getOriginalFileName(), material.getContentType(), material.getSizeBytes());
    }
}
