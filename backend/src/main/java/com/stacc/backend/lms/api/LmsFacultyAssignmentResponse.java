package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.content.LmsContentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** One assignment as faculty manage it. */
public record LmsFacultyAssignmentResponse(
        @Schema(example = "9")
        Long id,

        @Schema(example = "Arrays practice")
        String title,

        @Schema(description = "Plain text with line breaks. It is never HTML.",
                example = "Solve problems 1 to 5 from the arrays worksheet.")
        String instructions,

        @Schema(description = "The deadline, in UTC.", example = "2026-10-20T18:29:00Z")
        Instant dueAt,

        @Schema(example = "20.00")
        BigDecimal maxMarks,

        @Schema(description = "DRAFT is hidden from students. PUBLISHED is shown to them. ARCHIVED is kept but hidden.")
        LmsContentStatus status,

        @Schema(description = "When it was first published, or null for a draft. Edits never change it.",
                example = "2026-10-14T09:30:00Z")
        Instant publishedAt,

        @Schema(example = "2026-10-13T16:05:00Z")
        Instant createdAt,

        @Schema(example = "2026-10-14T09:30:00Z")
        Instant updatedAt) {

    static LmsFacultyAssignmentResponse of(LmsAssignment assignment) {
        return new LmsFacultyAssignmentResponse(assignment.getId(), assignment.getTitle(), assignment.getInstructions(),
                assignment.getDueAt(), assignment.getMaxMarks(), assignment.getStatus(), assignment.getPublishedAt(),
                assignment.getCreatedAt(), assignment.getUpdatedAt());
    }
}
