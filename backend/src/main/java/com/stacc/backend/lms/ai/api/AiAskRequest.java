package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A student's question about a course. It is the only thing the client sends: the student comes from the access
 * token, and the course content comes from the server.
 */
public record AiAskRequest(
        @Schema(description = "The question, 2 to 1,000 characters after trimming.", example = "What does third normal form remove?")
        @NotBlank @Size(max = 1000) String question) {
}
