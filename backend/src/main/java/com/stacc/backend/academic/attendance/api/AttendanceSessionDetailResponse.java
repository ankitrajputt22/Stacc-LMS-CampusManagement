package com.stacc.backend.academic.attendance.api;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** One session with its whole roster, in student ID order, for faculty. */
public record AttendanceSessionDetailResponse(
        @Schema(example = "8")
        Long sessionId,

        @Schema(example = "17")
        Long courseOfferingId,

        @Schema(example = "BCS301")
        String courseCode,

        @Schema(example = "Data Structures")
        String courseName,

        @Schema(example = "2026-10-19T04:30:00Z")
        Instant heldAt,

        @Schema(example = "Arrays introduction")
        String topic,

        @Schema(allowableValues = {"OPEN", "FINALIZED"}, example = "OPEN")
        String status,

        Instant finalizedAt,

        @Schema(description = "The students enrolled when the session was created.")
        List<AttendanceRecordResponse> records) {
}
