package com.stacc.backend.lms.api;

import com.stacc.backend.lms.content.LmsStudyMaterial;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * A new study material, sent as multipart/form-data. Everything else, such as where the file is
 * stored, its content type, its size, its status, and its place, is decided by the server.
 */
public record LmsMaterialUploadRequest(
        @Schema(example = "Lecture notes")
        @NotBlank(message = "Title is required")
        @Size(max = LmsStudyMaterial.MAX_TITLE_LENGTH, message = "Title must be at most " + LmsStudyMaterial.MAX_TITLE_LENGTH + " characters")
        String title,

        @Schema(description = "Optional plain text. HTML is not interpreted.", example = "Read before the first class.")
        @Size(max = LmsStudyMaterial.MAX_DESCRIPTION_LENGTH, message = "Description must be at most " + LmsStudyMaterial.MAX_DESCRIPTION_LENGTH + " characters")
        String description,

        @Schema(description = "PDF, TXT, DOC, DOCX, PPT, PPTX, XLS, XLSX, PNG, or JPEG, up to 25 MB by default.",
                type = "string", format = "binary")
        @NotNull(message = "Choose a file to upload")
        MultipartFile file) {
}
