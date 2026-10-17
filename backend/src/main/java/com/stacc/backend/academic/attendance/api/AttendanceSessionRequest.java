package com.stacc.backend.academic.attendance.api;

import java.time.Instant;

import com.stacc.backend.academic.attendance.AttendanceSession;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * When a class was held and its topic, for creating or changing a session. The course offering comes from the
 * route; the creator, status, students, and finalization are never taken from the client.
 */
public record AttendanceSessionRequest(
        @Schema(description = "When the class was held, as an ISO-8601 moment with a time zone, such as "
                + "2026-10-19T04:30:00Z. A date and time without a zone is refused, and the year must be between "
                + "2000 and 9999.", example = "2026-10-19T04:30:00Z")
        @NotNull(message = "The date and time are required")
        @SupportedHeldAt
        Instant heldAt,

        @Schema(description = "Optional plain text. Blank means no topic.", example = "Arrays introduction")
        @Size(max = AttendanceSession.MAX_TOPIC_LENGTH, message = "Topic must be at most 500 characters")
        String topic) {
}
