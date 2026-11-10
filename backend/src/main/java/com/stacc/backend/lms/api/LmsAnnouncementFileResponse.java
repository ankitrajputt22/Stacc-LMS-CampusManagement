package com.stacc.backend.lms.api;

import java.util.List;
import java.util.Map;

import com.stacc.backend.lms.announcement.LmsAnnouncementAttachment;
import io.swagger.v3.oas.annotations.media.Schema;

/** A file on an announcement, downloaded through its announcement's route. No storage key or address is ever sent. */
public record LmsAnnouncementFileResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "exam-timetable.pdf") String fileName,
        @Schema(example = "application/pdf") String contentType,
        @Schema(example = "48211") long sizeBytes) {

    static LmsAnnouncementFileResponse of(LmsAnnouncementAttachment attachment) {
        return new LmsAnnouncementFileResponse(attachment.getId(), attachment.getFileName(), attachment.getContentType(),
                attachment.getSizeBytes());
    }

    static List<LmsAnnouncementFileResponse> of(Map<Long, List<LmsAnnouncementAttachment>> byAnnouncement, Long announcementId) {
        return byAnnouncement.getOrDefault(announcementId, List.of()).stream().map(LmsAnnouncementFileResponse::of).toList();
    }
}
