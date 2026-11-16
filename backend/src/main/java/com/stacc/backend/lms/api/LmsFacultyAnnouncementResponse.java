package com.stacc.backend.lms.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.lms.announcement.LmsAnnouncement;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
import io.swagger.v3.oas.annotations.media.Schema;

/** One announcement as faculty manage it. */
public record LmsFacultyAnnouncementResponse(
        @Schema(example = "5")
        Long id,

        @Schema(example = "Mid-semester exam schedule")
        String title,

        @Schema(description = "The text with line breaks, shown as its format says. It is never HTML.", example = "The mid-semester exam is next Monday.")
        String message,

        @Schema(description = "DRAFT is hidden from students. PUBLISHED is shown to them. ARCHIVED is kept but hidden.")
        LmsContentStatus status,

        @Schema(description = "When it was first published, or null for a draft. Corrections never change it.", example = "2026-10-14T09:30:00Z")
        Instant publishedAt,

        @Schema(example = "2026-10-13T16:05:00Z")
        Instant createdAt,

        @Schema(example = "2026-10-14T09:30:00Z")
        Instant updatedAt,
        @Schema(description = "How to show the text: PLAIN exactly as written, FORMATTED with the small formatting set. Never HTML.", allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat messageFormat,

        @Schema(description = "The files on the announcement, oldest first.")
        List<LmsAnnouncementFileResponse> files) {

    static LmsFacultyAnnouncementResponse of(LmsAnnouncement announcement) {
        return of(announcement, List.of());
    }

    static LmsFacultyAnnouncementResponse of(LmsAnnouncement announcement, List<LmsAnnouncementFileResponse> files) {
        return new LmsFacultyAnnouncementResponse(announcement.getId(), announcement.getTitle(), announcement.getMessage(),
                announcement.getStatus(), announcement.getPublishedAt(), announcement.getCreatedAt(), announcement.getUpdatedAt(),
                announcement.getMessageFormat(), files);
    }

    LmsFacultyAnnouncementResponse withFiles(List<LmsAnnouncementFileResponse> listed) {
        return new LmsFacultyAnnouncementResponse(id, title, message, status, publishedAt, createdAt, updatedAt, messageFormat, listed);
    }
}
