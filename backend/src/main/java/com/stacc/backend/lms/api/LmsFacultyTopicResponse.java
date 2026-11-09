package com.stacc.backend.lms.api;

import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTextFormat;
import io.swagger.v3.oas.annotations.media.Schema;

/** One topic as faculty manage it. */
public record LmsFacultyTopicResponse(
        @Schema(example = "31")
        Long id,

        @Schema(example = "Course overview")
        String title,

        @Schema(description = "The text with line breaks, or null, shown as its format says. It is never HTML.", example = "Read the syllabus before the first class.")
        String content,

        @Schema(description = "The topic's place in its module, starting at 1.", example = "1")
        int position,

        @Schema(description = "DRAFT is hidden from students. PUBLISHED is shown once its module is published too.")
        LmsContentStatus status,
        @Schema(description = "How to show the text: PLAIN exactly as written, FORMATTED with the small formatting set. Never HTML.", allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat contentFormat,
        @Schema(description = "Whether the page counts towards its module's completion and students' progress.")
        boolean required) {

    static LmsFacultyTopicResponse of(LmsTopic topic) {
        return new LmsFacultyTopicResponse(
                topic.getId(), topic.getTitle(), topic.getContent(), topic.getPosition(), topic.getStatus(), topic.getContentFormat(),
                topic.isRequired());
    }
}
