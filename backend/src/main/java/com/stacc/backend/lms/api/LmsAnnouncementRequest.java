package com.stacc.backend.lms.api;

import com.stacc.backend.lms.announcement.LmsAnnouncement;
import com.stacc.backend.lms.content.LmsTextFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The title and message of an announcement, for adding or editing one. The course, status, and
 * publication time are not part of it: they come from the route and the publish and archive actions.
 */
public record LmsAnnouncementRequest(
        @Schema(example = "Mid-semester exam schedule")
        @NotBlank(message = "Title is required")
        @Size(max = LmsAnnouncement.MAX_TITLE_LENGTH, message = "Title must be at most " + LmsAnnouncement.MAX_TITLE_LENGTH + " characters")
        String title,

        @Schema(description = "Shown as its format says. Line breaks are kept; HTML is never interpreted.", example = "The mid-semester exam is next Monday.")
        @NotBlank(message = "Message is required")
        @Size(max = LmsAnnouncement.MAX_MESSAGE_LENGTH, message = "Message must be at most 10,000 characters")
        String message,
        @Schema(description = "PLAIN shows the text exactly as typed; FORMATTED uses the small formatting set (Markdown headings, lists, links, code, tables, and $maths$), never HTML. Left out, a new text is PLAIN and an edited one keeps its format.", allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat messageFormat) {
}
