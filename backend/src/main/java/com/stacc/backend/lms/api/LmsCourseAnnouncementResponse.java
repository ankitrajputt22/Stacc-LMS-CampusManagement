package com.stacc.backend.lms.api;

import java.time.Instant;

import com.stacc.backend.lms.announcement.LmsAnnouncement;
import io.swagger.v3.oas.annotations.media.Schema;

/** One published announcement, as a student sees it. Everything a student receives is published. */
public record LmsCourseAnnouncementResponse(
        @Schema(example = "5")
        Long id,

        @Schema(example = "Mid-semester exam schedule")
        String title,

        @Schema(description = "Plain text with line breaks. It is never HTML.", example = "The mid-semester exam is next Monday.")
        String message,

        @Schema(description = "When it was first published.", example = "2026-10-14T09:30:00Z")
        Instant publishedAt) {

    static LmsCourseAnnouncementResponse of(LmsAnnouncement announcement) {
        return new LmsCourseAnnouncementResponse(
                announcement.getId(), announcement.getTitle(), announcement.getMessage(), announcement.getPublishedAt());
    }
}
