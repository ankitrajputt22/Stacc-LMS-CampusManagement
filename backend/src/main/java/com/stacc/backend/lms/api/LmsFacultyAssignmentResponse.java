package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import com.stacc.backend.lms.outcome.BloomLevel;

/** One assignment as faculty manage it. */
public record LmsFacultyAssignmentResponse(
        @Schema(example = "9")
        Long id,

        @Schema(example = "Arrays practice")
        String title,

        @Schema(description = "The text with line breaks, shown as its format says. It is never HTML.",
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
        Instant updatedAt,
        @Schema(description = "How to show the text: PLAIN exactly as written, FORMATTED with the small formatting set. Never HTML.", allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat instructionsFormat,
        @Schema(description = "The Bloom level it works at, or null.")
        BloomLevel bloomLevel,
        @Schema(description = "The course outcomes it assesses, by code.")
        List<LmsOutcomeResponse> outcomes,
        @Schema(description = "The rubric it is graded with, or null.")
        LmsRubricResponse rubric,
        @Schema(description = "When it starts taking work; null means from publication.")
        Instant availableFrom,
        @Schema(description = "After this no work is taken; null means no final closing time.")
        Instant availableUntil,
        @Schema(description = "Whether work handed in after the due date is taken (and marked late).")
        boolean allowLateSubmissions,
        @Schema(description = "Attempts each student may make; null means unlimited.")
        Integer maxAttempts) {

    static LmsFacultyAssignmentResponse of(LmsAssignment assignment) {
        return new LmsFacultyAssignmentResponse(assignment.getId(), assignment.getTitle(), assignment.getInstructions(),
                assignment.getDueAt(), assignment.getMaxMarks(), assignment.getStatus(), assignment.getPublishedAt(),
                assignment.getCreatedAt(), assignment.getUpdatedAt(), assignment.getInstructionsFormat(),
                assignment.getBloomLevel(), assignment.getOutcomes().stream().map(LmsOutcomeResponse::of).toList(),
                LmsRubricResponse.ofNullable(assignment.getRubric()), assignment.getAvailableFrom(),
                assignment.getAvailableUntil(), assignment.isAllowLateSubmissions(), assignment.getMaxAttempts());
    }
}
