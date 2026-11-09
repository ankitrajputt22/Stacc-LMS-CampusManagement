package com.stacc.backend.lms.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** The earlier LMS course to copy from. Whether the faculty member may use it is checked on the server. */
public record LmsCourseCopyRequest(
        @Schema(description = "One of the copy sources of this course.", example = "17")
        @NotNull(message = "Choose the course to copy from")
        Long sourceLmsCourseId) {
}
