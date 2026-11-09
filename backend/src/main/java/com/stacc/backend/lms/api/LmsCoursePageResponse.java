package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.lms.content.LmsTextFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One published page of a course, as a student opens it, with the published pages around it. A page is a topic of a
 * module: it is shown only when the course, its module, and the page are all available to the student.
 */
public record LmsCoursePageResponse(
        @Schema(example = "31")
        Long id,
        @Schema(example = "Course overview")
        String title,
        @Schema(description = "The text, or null, shown as its format says. It is never HTML.")
        String content,
        @Schema(allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat contentFormat,
        @Schema(example = "12")
        Long moduleId,
        @Schema(example = "Unit 1: Arrays and Linked Lists")
        String moduleTitle,
        @ArraySchema(arraySchema = @Schema(description = "The published study materials, in page order. Often empty."))
        List<LmsCourseMaterialResponse> materials,
        @Schema(description = "The published page before this one in course order, or null for the first.")
        LmsPageLink previous,
        @Schema(description = "The published page after this one in course order, or null for the last.")
        LmsPageLink next,
        boolean required,
        @Schema(description = "Whether this student marked the page complete.")
        boolean completed,
        @Schema(description = "Whether the page's module is locked for this student; then the text and files are left out.")
        boolean locked,
        @Schema(description = "The module to complete first, when locked; otherwise null.")
        String waitingFor) {
}
