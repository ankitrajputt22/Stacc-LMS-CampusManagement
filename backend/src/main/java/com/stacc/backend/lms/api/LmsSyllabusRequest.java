package com.stacc.backend.lms.api;

import com.stacc.backend.lms.syllabus.LmsSyllabus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The syllabus draft, as formatted text. The course comes from the route; nothing else is taken from the client. */
public record LmsSyllabusRequest(
        @Schema(description = "Formatted text (Markdown headings, lists, links, code, tables, and $maths$), never HTML.",
                example = "## Course overview\n\nData structures and their costs.")
        @NotBlank(message = "Syllabus text is required")
        @Size(max = LmsSyllabus.MAX_CONTENT_LENGTH, message = "Syllabus text must be at most 30,000 characters")
        String content) {
}
