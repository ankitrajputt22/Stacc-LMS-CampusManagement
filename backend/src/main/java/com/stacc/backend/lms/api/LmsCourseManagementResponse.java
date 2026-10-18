package com.stacc.backend.lms.api;

import com.stacc.backend.lms.management.LmsCourseChange;
import io.swagger.v3.oas.annotations.media.Schema;

/** An LMS course after a faculty management change. */
public record LmsCourseManagementResponse(
        @Schema(example = "42")
        Long lmsCourseId,

        @Schema(example = "31")
        Long courseOfferingId,

        @Schema(allowableValues = {"DRAFT", "PUBLISHED", "ARCHIVED"}, example = "PUBLISHED")
        String lmsCourseStatus,

        @Schema(description = "How many current students of the offering have an active LMS membership after the "
                + "change: their course and semester enrollments are ENROLLED and their student profile is ACTIVE. "
                + "Null when the change did not synchronize memberships, as for archiving.", example = "28",
                nullable = true)
        Integer synchronizedCount) {

    static LmsCourseManagementResponse of(LmsCourseChange change) {
        return new LmsCourseManagementResponse(change.lmsCourseId(), change.courseOfferingId(), change.status().name(),
                change.synchronizedCount());
    }
}
