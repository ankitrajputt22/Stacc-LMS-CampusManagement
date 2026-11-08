package com.stacc.backend.lms.api;

import java.time.Instant;

import com.stacc.backend.lms.syllabus.LmsSyllabus;
import io.swagger.v3.oas.annotations.media.Schema;

/** A course's syllabus as faculty manage it: the draft and the published version. Nulls when none is saved yet. */
public record LmsFacultySyllabusResponse(
        @Schema(description = "The saved draft, or null when no syllabus is saved yet.")
        String draftContent,
        Instant draftUpdatedAt,
        @Schema(description = "The version students read, or null when it was never published.")
        String publishedContent,
        Instant firstPublishedAt,
        Instant lastPublishedAt,
        @Schema(description = "Whether the draft differs from what students read.")
        boolean unpublishedChanges) {

    static LmsFacultySyllabusResponse of(LmsSyllabus syllabus) {
        return syllabus == null ? new LmsFacultySyllabusResponse(null, null, null, null, null, false)
                : new LmsFacultySyllabusResponse(syllabus.getDraftContent(), syllabus.getDraftUpdatedAt(),
                        syllabus.getPublishedContent(), syllabus.getFirstPublishedAt(), syllabus.getLastPublishedAt(),
                        syllabus.hasUnpublishedChanges());
    }
}
