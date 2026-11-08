package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTextFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** One published topic, as a student sees it. */
public record LmsCourseTopicResponse(
        @Schema(example = "31")
        Long id,

        @Schema(example = "Course overview")
        String title,

        @Schema(description = "The text with line breaks, or null, shown as its format says. It is never HTML.", example = "Read the syllabus before the first class.")
        String content,

        @ArraySchema(arraySchema = @Schema(description = "The published study materials, in topic order. Often empty."))
        List<LmsCourseMaterialResponse> materials,
        @Schema(description = "How to show the text: PLAIN exactly as written, FORMATTED with the small formatting set. Never HTML.", allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat contentFormat,
        @Schema(description = "Whether the page counts towards the module's completion and the student's progress.")
        boolean required,
        @Schema(description = "Whether this student marked the page complete.")
        boolean completed) {

    static LmsCourseTopicResponse of(LmsTopic topic, List<LmsCourseMaterialResponse> materials) {
        return of(topic, materials, false);
    }

    static LmsCourseTopicResponse of(LmsTopic topic, List<LmsCourseMaterialResponse> materials, boolean completed) {
        return new LmsCourseTopicResponse(topic.getId(), topic.getTitle(), topic.getContent(), materials, topic.getContentFormat(),
                topic.isRequired(), completed);
    }

    /** The page in a locked module: its title and flags only, never its text or files. */
    LmsCourseTopicResponse withoutContent() {
        return new LmsCourseTopicResponse(id, title, null, List.of(), contentFormat, required, completed);
    }
}
