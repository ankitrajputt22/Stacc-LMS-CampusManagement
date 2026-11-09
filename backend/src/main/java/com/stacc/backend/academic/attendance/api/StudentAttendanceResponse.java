package com.stacc.backend.academic.attendance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The signed-in student's own finalized attendance, one entry per course offering that has at least one
 * finalized session. Open sessions are never included. There is no threshold, warning, or eligibility here.
 */
public record StudentAttendanceResponse(List<Course> courses) {

    public record Course(
            @Schema(example = "17")
            Long courseOfferingId,

            @Schema(example = "BCS301")
            String courseCode,

            @Schema(example = "Data Structures")
            String courseName,

            @Schema(example = "3")
            int semesterNumber,

            @Schema(example = "BTECH")
            String programCode,

            @Schema(example = "2026-27")
            String academicSessionCode,

            @Schema(description = "Finalized sessions the student was on the roster of.", example = "10")
            int totalSessions,

            @Schema(example = "8")
            int presentCount,

            @Schema(example = "2")
            int absentCount,

            @Schema(description = "presentCount / totalSessions * 100, rounded half up to two decimal places. "
                    + "Calculated on each request, never stored.", example = "80.00")
            BigDecimal attendancePercentage,

            @Schema(description = "The sessions, latest class first.")
            List<Session> sessions) {
    }

    public record Session(
            @Schema(example = "8")
            Long attendanceSessionId,

            @Schema(example = "2026-10-19T04:30:00Z")
            Instant heldAt,

            @Schema(description = "Plain text, or null.", example = "Arrays introduction")
            String topic,

            @Schema(allowableValues = {"PRESENT", "ABSENT"}, example = "PRESENT")
            String status) {
    }
}
