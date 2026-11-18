package com.stacc.backend.lms.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record LmsCourseProgressResponse(
        @Schema(description = "Required pages the student can see.", example = "10")
        int requiredPages,
        @Schema(description = "Of those, the ones this student marked complete.", example = "6")
        int completedRequiredPages,
        @Schema(description = "The next required page to complete, in course order, in an open module; null when none is left.")
        LmsPageLink next) {
}
