package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * A new attempt, sent as multipart/form-data. Both parts are optional, but an attempt needs non-blank
 * text, at least one file, or both. Who is submitting, for which enrollment, and when, are decided by the
 * server; anything else in the request is ignored.
 */
public record LmsSubmissionRequest(
        @Schema(description = "Optional plain text, up to 20,000 characters. Line breaks are kept; HTML is not interpreted.",
                example = "My answers are in the attached file.")
        @Size(max = LmsSubmissionAttempt.MAX_TEXT_LENGTH, message = "Text response must be at most 20,000 characters")
        String textContent,

        @ArraySchema(
                arraySchema = @Schema(description = "Optional files, up to 3 by default, each up to 25 MB: PDF, TXT, DOC, "
                        + "DOCX, PPT, PPTX, XLS, XLSX, PNG, or JPEG. Send each file as its own part named files."),
                schema = @Schema(type = "string", format = "binary"))
        List<MultipartFile> files) {
}
