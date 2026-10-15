package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.lms.content.LmsTopic;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** One published topic, as a student sees it. */
public record LmsCourseTopicResponse(
        @Schema(example = "31")
        Long id,

        @Schema(example = "Course overview")
        String title,

        @Schema(description = "Plain text with line breaks, or null. It is never HTML.", example = "Read the syllabus before the first class.")
        String content,

        @ArraySchema(arraySchema = @Schema(description = "The published study materials, in topic order. Often empty."))
        List<LmsCourseMaterialResponse> materials) {

    static LmsCourseTopicResponse of(LmsTopic topic, List<LmsCourseMaterialResponse> materials) {
        return new LmsCourseTopicResponse(topic.getId(), topic.getTitle(), topic.getContent(), materials);
    }
}
