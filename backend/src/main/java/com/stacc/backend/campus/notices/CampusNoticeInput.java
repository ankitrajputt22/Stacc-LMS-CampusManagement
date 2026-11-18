package com.stacc.backend.campus.notices;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
