package com.stacc.backend.academic.attendance.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** One student on a session's roster. No enrollment, profile, or account ID is sent, and no marker. */
public record AttendanceRecordResponse(
        @Schema(description = "The ID to send when saving this student's status.", example = "101")
        Long attendanceRecordId,

        @Schema(description = "The student's college ID. There is no name to show.", example = "2408400100011")
        String studentLoginId,

        @Schema(allowableValues = {"UNMARKED", "PRESENT", "ABSENT"}, example = "UNMARKED")
        String status,

        @Schema(description = "When it was last marked present or absent, or null while unmarked.")
        Instant markedAt) {
}
