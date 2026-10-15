package com.stacc.backend.lms.api;

import com.stacc.backend.lms.content.LmsModule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The title and description of a module, for adding or editing one. The course, status, and position
 * are not part of it: they are set by the route and by the publish, archive, and reorder actions.
 */
public record LmsModuleRequest(
        @Schema(example = "Introduction")
        @NotBlank(message = "Title is required")
        @Size(max = LmsModule.MAX_TITLE_LENGTH, message = "Title must be at most " + LmsModule.MAX_TITLE_LENGTH + " characters")
        String title,

        @Schema(description = "Optional plain text. HTML is not interpreted.", example = "What the course covers and how it is organised.")
        @Size(max = LmsModule.MAX_DESCRIPTION_LENGTH, message = "Description must be at most " + LmsModule.MAX_DESCRIPTION_LENGTH + " characters")
        String description) {
}
