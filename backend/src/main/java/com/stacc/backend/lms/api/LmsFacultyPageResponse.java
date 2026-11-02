package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One page of a course a faculty member teaches, drafts included, so they can see it as students will once it is
 * published. The pages around it are the other pages that are not archived, in course order.
 */
public record LmsFacultyPageResponse(
        @Schema(example = "42")
        Long lmsCourseId,
        @Schema(example = "BCS301")
        String courseCode,
        @Schema(example = "Data Structures")
        String courseName,
        @Schema(allowableValues = {"DRAFT", "PUBLISHED"})
        String lmsCourseStatus,
        @Schema(example = "31")
        Long id,
        @Schema(example = "Course overview")
        String title,
        @Schema(description = "The text, or null, shown as its format says. It is never HTML.")
        String content,
        @Schema(allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat contentFormat,
        @Schema(description = "DRAFT is hidden from students. PUBLISHED is shown once its module is published too.")
        LmsContentStatus status,
        @Schema(example = "12")
        Long moduleId,
        @Schema(example = "Unit 1: Arrays and Linked Lists")
        String moduleTitle,
        LmsContentStatus moduleStatus,
        @ArraySchema(arraySchema = @Schema(description = "The study materials that are not archived, in page order."))
        List<LmsFacultyMaterialResponse> materials,
        @Schema(description = "The page before this one, or null.")
        LmsPageLink previous,
        @Schema(description = "The page after this one, or null.")
        LmsPageLink next) {
}
