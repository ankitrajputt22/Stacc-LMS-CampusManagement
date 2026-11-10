package com.stacc.backend.lms.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.lms.announcement.LmsAnnouncement;
import com.stacc.backend.lms.content.LmsTextFormat;
import io.swagger.v3.oas.annotations.media.Schema;

/** One published announcement, as a student sees it. Everything a student receives is published. */
public record LmsCourseAnnouncementResponse(
        @Schema(example = "5")
        Long id,

        @Schema(example = "Mid-semester exam schedule")
        String title,

        @Schema(description = "The text with line breaks, shown as its format says. It is never HTML.", example = "The mid-semester exam is next Monday.")
        String message,

        @Schema(description = "When it was first published.", example = "2026-10-14T09:30:00Z")
        Instant publishedAt,
        @Schema(description = "How to show the text: PLAIN exactly as written, FORMATTED with the small formatting set. Never HTML.", allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat messageFormat,

        @Schema(description = "The files on the announcement, oldest first.")
        List<LmsAnnouncementFileResponse> files) {

    public LmsCourseAnnouncementResponse(Long id, String title, String message, Instant publishedAt, LmsTextFormat messageFormat) {
        this(id, title, message, publishedAt, messageFormat, List.of());
    }

    static LmsCourseAnnouncementResponse of(LmsAnnouncement announcement) {
        return new LmsCourseAnnouncementResponse(
                announcement.getId(), announcement.getTitle(), announcement.getMessage(), announcement.getPublishedAt(),
                announcement.getMessageFormat());
    }

    LmsCourseAnnouncementResponse withFiles(List<LmsAnnouncementFileResponse> listed) {
        return new LmsCourseAnnouncementResponse(id, title, message, publishedAt, messageFormat, listed);
    }
}
