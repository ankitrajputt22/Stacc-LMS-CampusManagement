package com.stacc.backend.campus.notices;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Campus notices as clients see them. Readers get only the published text; admins get the whole notice. */
public final class CampusNoticeResponses {

    private CampusNoticeResponses() {
    }

    /** A published notice in the feed. */
    public record CampusNoticeResponse(
            Long noticeId,
            @Schema(description = "Plain text.") String title,
            @Schema(description = "Plain text.") String message,
            Instant publishedAt) {

        static CampusNoticeResponse of(CampusNotice notice) {
            return new CampusNoticeResponse(notice.getId(), notice.getTitle(), notice.getMessage(), notice.getPublishedAt());
        }
    }

    public record CampusNoticePage(List<CampusNoticeResponse> items, int page, int size, long totalElements,
            int totalPages) {
    }

    /** A notice with its visibility and lifecycle, for admins. */
    public record AdminCampusNoticeResponse(
            Long noticeId,
            String title,
            String message,
            boolean visibleToStudent,
            boolean visibleToFaculty,
            boolean visibleToAdmin,
            @Schema(allowableValues = {"DRAFT", "PUBLISHED", "ARCHIVED"}) String status,
            @Schema(nullable = true, description = "Set on the first publication and never changed.") Instant publishedAt,
            Instant createdAt,
            Instant updatedAt) {

        static AdminCampusNoticeResponse of(CampusNotice notice) {
            return new AdminCampusNoticeResponse(notice.getId(), notice.getTitle(), notice.getMessage(),
                    notice.isVisibleToStudent(), notice.isVisibleToFaculty(), notice.isVisibleToAdmin(),
                    notice.getStatus().name(), notice.getPublishedAt(), notice.getCreatedAt(), notice.getUpdatedAt());
        }
    }

    public record AdminCampusNoticePage(List<AdminCampusNoticeResponse> items, int page, int size, long totalElements,
            int totalPages) {
    }
}
