package com.stacc.backend.academic.attendance.api;

import java.time.Instant;

import com.stacc.backend.academic.attendance.AttendanceSession;
import io.swagger.v3.oas.annotations.media.Schema;

/** One session as faculty see it in a list. Who created it is not sent. */
public record AttendanceSessionResponse(
        @Schema(example = "8")
        Long id,

        @Schema(description = "When the class was held, in UTC.", example = "2026-10-19T04:30:00Z")
        Instant heldAt,

        @Schema(description = "Plain text, or null.", example = "Arrays introduction")
        String topic,

        @Schema(allowableValues = {"OPEN", "FINALIZED"}, example = "OPEN")
        String status,

        @Schema(description = "When it was first finalized, or null while it is open.")
        Instant finalizedAt) {

    static AttendanceSessionResponse of(AttendanceSession session) {
        return new AttendanceSessionResponse(
                session.getId(), session.getHeldAt(), session.getTopic(), session.getStatus().name(), session.getFinalizedAt());
    }
}
