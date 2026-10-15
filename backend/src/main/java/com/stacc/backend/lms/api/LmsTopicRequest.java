package com.stacc.backend.lms.api;

import com.stacc.backend.lms.content.LmsTopic;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The title and text of a topic, for adding or editing one. The module, status, and position are not
 * part of it: they are set by the route and by the publish, archive, and reorder actions.
 */
public record LmsTopicRequest(
        @Schema(example = "Course overview")
        @NotBlank(message = "Title is required")
        @Size(max = LmsTopic.MAX_TITLE_LENGTH, message = "Title must be at most " + LmsTopic.MAX_TITLE_LENGTH + " characters")
        String title,

        @Schema(description = "Optional plain text. Line breaks are kept; HTML is not interpreted.", example = "Read the syllabus before the first class.")
        @Size(max = LmsTopic.MAX_CONTENT_LENGTH, message = "Content must be at most 20,000 characters")
        String content) {
}
