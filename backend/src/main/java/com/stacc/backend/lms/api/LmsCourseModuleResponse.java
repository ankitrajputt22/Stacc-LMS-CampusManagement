package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.lms.content.LmsModule;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** One published module with its published topics, as a student sees it. */
public record LmsCourseModuleResponse(
        @Schema(example = "12")
        Long id,

        @Schema(example = "Introduction")
        String title,

        @Schema(description = "Plain text, or null.", example = "What the course covers and how it is organised.")
        String description,

        @ArraySchema(arraySchema = @Schema(description = "The published topics, in module order."))
        List<LmsCourseTopicResponse> topics) {

    static LmsCourseModuleResponse of(LmsModule module, List<LmsCourseTopicResponse> topics) {
        return new LmsCourseModuleResponse(module.getId(), module.getTitle(), module.getDescription(), topics);
    }
}
