package com.stacc.backend.lms.api;

import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTextFormat;
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

        @Schema(description = "Optional text, shown as its format says. Line breaks are kept; HTML is never interpreted.", example = "Read the syllabus before the first class.")
        @Size(max = LmsTopic.MAX_CONTENT_LENGTH, message = "Content must be at most 20,000 characters")
        String content,
        @Schema(description = "PLAIN shows the text exactly as typed; FORMATTED uses the small formatting set (Markdown headings, lists, links, code, tables, and $maths$), never HTML. Left out, a new text is PLAIN and an edited one keeps its format.", allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat contentFormat) {
}
