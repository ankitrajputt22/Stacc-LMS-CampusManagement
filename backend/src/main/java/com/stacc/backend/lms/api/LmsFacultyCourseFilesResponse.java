package com.stacc.backend.lms.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** The Files list of a course a faculty member teaches, with the course's own details and state. */
public record LmsFacultyCourseFilesResponse(
        @Schema(example = "42")
        Long lmsCourseId,
        @Schema(example = "BCS301")
        String courseCode,
        @Schema(example = "Data Structures")
        String courseName,
        @Schema(description = "Students see nothing in a DRAFT course.", allowableValues = {"DRAFT", "PUBLISHED"})
        String lmsCourseStatus,
        @ArraySchema(arraySchema = @Schema(description = "Every file that is not archived, in course order."))
        List<LmsFacultyCourseFileResponse> files) {
}
