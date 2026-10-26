package com.stacc.backend.campus.notices;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What an admin sends to create or correct a notice: plain text and the roles that may see it, at least one. It never
 * carries a status or publication time; those come from the publish and archive routes.
 */
public record CampusNoticeInput(
        @Schema(description = "Plain text.", example = "Library closed on Friday")
        @NotBlank(message = "Title is required")
        @Size(max = CampusNotice.MAX_TITLE_LENGTH, message = "Title must be at most 200 characters")
        String title,

        @Schema(description = "Plain text.")
        @NotBlank(message = "Message is required")
        @Size(max = CampusNotice.MAX_MESSAGE_LENGTH, message = "Message must be at most 10000 characters")
        String message,

        @NotNull(message = "Visible to students is required")
        Boolean visibleToStudent,

        @NotNull(message = "Visible to faculty is required")
        Boolean visibleToFaculty,

        @NotNull(message = "Visible to admins is required")
        Boolean visibleToAdmin) {

    CampusNotice.Content content() {
        return new CampusNotice.Content(title, message, Boolean.TRUE.equals(visibleToStudent),
                Boolean.TRUE.equals(visibleToFaculty), Boolean.TRUE.equals(visibleToAdmin));
    }
}
