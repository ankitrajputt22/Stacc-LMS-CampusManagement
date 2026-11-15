package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A student's question about a course. It is the only thing the client sends: the student comes from the access
 * token, and the course content comes from the server.
 */
public record AiAskRequest(
        @Schema(description = "The question, 2 to 1,000 characters after trimming.", example = "What does third normal form remove?")
        @NotBlank @Size(max = 1000) String question,
        @Schema(description = "True asks \"Course + my notes\" (v0.13.6): the student's own notes in this course are "
                + "searched too. Leave it out or false for the course alone.", nullable = true)
        Boolean includeMyNotes,
        @Schema(description = "COURSE (the default), COURSE_AND_NOTES, or NOTES_ONLY (v0.13.9). Overrides includeMyNotes.",
                nullable = true)
        AiAskMode mode,
        @Schema(description = "Only this module's published pages and files (v0.13.9); not with NOTES_ONLY.", nullable = true)
        @Positive Long moduleId) {

    public AiAskRequest(String question) {
        this(question, null, null, null);
    }

    public AiAskRequest(String question, Boolean includeMyNotes) {
        this(question, includeMyNotes, null, null);
    }

    AiAskMode effectiveMode() {
        if (mode != null) {
            return mode;
        }
        return Boolean.TRUE.equals(includeMyNotes) ? AiAskMode.COURSE_AND_NOTES : AiAskMode.COURSE;
    }

    boolean withMyNotes() {
        return effectiveMode() != AiAskMode.COURSE;
    }
}
