package com.stacc.backend.lms.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One published file of a course, as a student sees it in the course's Files list: a study material with the page and
 * module it is on. Where the file is stored is never sent; it downloads through the API.
 */
public record LmsCourseFileResponse(
        @Schema(description = "Used to download the file.", example = "7")
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
        @Schema(description = "The page the file is on.", example = "31")
        Long pageId,
        @Schema(example = "Singly linked lists")
        String pageTitle,
        @Schema(example = "Unit 1: Arrays and Linked Lists")
        String moduleTitle) {

    static LmsCourseFileResponse of(LmsCourseMaterialResponse file, LmsCourseTopicResponse page,
            LmsCourseModuleResponse module) {
        return new LmsCourseFileResponse(file.id(), file.title(), file.description(), file.originalFileName(),
                file.contentType(), file.sizeBytes(), file.uploadedAt(), page.id(), page.title(), module.title());
    }
}
