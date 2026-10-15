package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsModule;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** One module as faculty manage it, with its topics that are not archived. */
public record LmsFacultyModuleContentResponse(
        @Schema(example = "12")
        Long id,

        @Schema(example = "Introduction")
        String title,

        @Schema(description = "Plain text, or null.", example = "What the course covers and how it is organised.")
        String description,

        @Schema(description = "The module's place in its course, starting at 1.", example = "1")
        int position,

        @Schema(description = "DRAFT is hidden from students. PUBLISHED is shown with its published topics.")
        LmsContentStatus status,

        @ArraySchema(arraySchema = @Schema(description = "The topics that are not archived, in module order."))
        List<LmsFacultyTopicContentResponse> topics) {

    static LmsFacultyModuleContentResponse of(LmsModule module, List<LmsFacultyTopicContentResponse> topics) {
        return new LmsFacultyModuleContentResponse(
                module.getId(), module.getTitle(), module.getDescription(), module.getPosition(), module.getStatus(), topics);
    }
}
