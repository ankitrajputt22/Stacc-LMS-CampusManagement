package com.stacc.backend.lms.api;

import java.time.Instant;

import com.stacc.backend.lms.content.LmsContentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One file of a course a faculty member teaches, drafts included: a study material that is not archived, with the page
 * and module it is on and their statuses, so the Files list can say whether students see it.
 */
public record LmsFacultyCourseFileResponse(
        @Schema(example = "7")
        Long id,
        @Schema(example = "Lecture notes")
        String title,
        @Schema(description = "Plain text, or null.")
        String description,
        @Schema(example = "Lecture notes.pdf")
        String originalFileName,
        @Schema(example = "application/pdf")
        String contentType,
        @Schema(example = "1887436")
        long sizeBytes,
        @Schema(example = "2026-10-14T09:30:00Z")
        Instant uploadedAt,
        @Schema(description = "DRAFT is hidden from students. PUBLISHED is shown once its page and module are too.")
        LmsContentStatus status,
        @Schema(example = "31")
        Long pageId,
        @Schema(example = "Singly linked lists")
        String pageTitle,
        LmsContentStatus pageStatus,
        @Schema(example = "Unit 1: Arrays and Linked Lists")
        String moduleTitle,
        LmsContentStatus moduleStatus) {

    static LmsFacultyCourseFileResponse of(LmsFacultyMaterialResponse file, LmsFacultyTopicContentResponse page,
            LmsFacultyModuleContentResponse module) {
        return new LmsFacultyCourseFileResponse(file.id(), file.title(), file.description(), file.originalFileName(),
                file.contentType(), file.sizeBytes(), file.uploadedAt(), file.status(), page.id(), page.title(),
                page.status(), module.title(), module.status());
    }
}
