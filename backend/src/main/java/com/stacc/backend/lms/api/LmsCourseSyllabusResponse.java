package com.stacc.backend.lms.api;

import java.time.Instant;

import com.stacc.backend.lms.syllabus.LmsSyllabus;
import io.swagger.v3.oas.annotations.media.Schema;

/** The published syllabus of a course, as a student reads it, or nulls when none is published yet. */
public record LmsCourseSyllabusResponse(
        @Schema(description = "Formatted text, or null when no syllabus is published yet. It is never HTML.")
        String content,
        @Schema(description = "When the version shown was published, or null.", example = "2026-10-14T09:30:00Z")
        Instant publishedAt) {

    static LmsCourseSyllabusResponse of(LmsSyllabus syllabus) {
        return syllabus == null ? new LmsCourseSyllabusResponse(null, null)
                : new LmsCourseSyllabusResponse(syllabus.getPublishedContent(), syllabus.getLastPublishedAt());
    }
}
