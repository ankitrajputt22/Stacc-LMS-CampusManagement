package com.stacc.backend.lms.api;

import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsModule;
import io.swagger.v3.oas.annotations.media.Schema;

/** One module as faculty manage it, without its topics. Returned after a change to the module. */
public record LmsFacultyModuleResponse(
        @Schema(example = "12")
        Long id,

        @Schema(example = "Introduction")
        String title,

        @Schema(description = "Plain text, or null.", example = "What the course covers and how it is organised.")
        String description,

        @Schema(description = "The module's place in its course, starting at 1.", example = "1")
        int position,

        @Schema(description = "DRAFT is hidden from students. PUBLISHED is shown with its published topics.")
        LmsContentStatus status) {

    static LmsFacultyModuleResponse of(LmsModule module) {
        return new LmsFacultyModuleResponse(
                module.getId(), module.getTitle(), module.getDescription(), module.getPosition(), module.getStatus());
    }
}
