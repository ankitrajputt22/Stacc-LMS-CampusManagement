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
        List<LmsCourseTopicResponse> topics,
        @Schema(description = "Whether students must complete the module before this one first.")
        boolean requiresPrevious,
        @Schema(description = "Whether the module is locked for this student. A locked module's pages show no text or files.")
        boolean locked,
        @Schema(description = "Whether this student completed every required page of the module.")
        boolean completed,
        @Schema(description = "The module to complete first, when locked; otherwise null.")
        String waitingFor) {

    static LmsCourseModuleResponse of(LmsModule module, List<LmsCourseTopicResponse> topics) {
        return new LmsCourseModuleResponse(module.getId(), module.getTitle(), module.getDescription(), topics,
                module.isRequiresPrevious(), false, false, null);
    }
}
