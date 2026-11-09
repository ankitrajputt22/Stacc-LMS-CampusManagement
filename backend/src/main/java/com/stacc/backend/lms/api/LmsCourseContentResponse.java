package com.stacc.backend.lms.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** The published content of one LMS course, as a student sees it. Everything here is published. */
public record LmsCourseContentResponse(
        @ArraySchema(arraySchema = @Schema(description = "The published modules, in course order. Empty when nothing is published yet."))
        List<LmsCourseModuleResponse> modules) {
}
