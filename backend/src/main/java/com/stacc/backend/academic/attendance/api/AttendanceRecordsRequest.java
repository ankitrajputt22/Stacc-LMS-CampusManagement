package com.stacc.backend.academic.attendance.api;

import java.util.List;

import com.stacc.backend.academic.attendance.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Statuses to save for some or all of a session's records. Each record is named by the ID the server gave it;
 * a student, enrollment, or account ID is never accepted.
 */
public record AttendanceRecordsRequest(
        @Schema(description = "The records to change, each at most once. Records not listed keep their status.")
        @NotNull(message = "Records are required")
        @Size(max = 1000, message = "Send at most 1,000 records at a time")
        List<@Valid @NotNull(message = "A record is required") Change> records) {

    public record Change(
            @Schema(description = "The attendanceRecordId from the session's roster.", example = "101")
            @NotNull(message = "The attendance record is required")
            Long attendanceRecordId,

            @Schema(description = "PRESENT or ABSENT; UNMARKED only while the session is open.", example = "PRESENT")
            @NotNull(message = "The status is required")
            AttendanceStatus status) {
    }
}
