package com.stacc.backend.academic.admin;

import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.academic.calendar.AcademicCalendarEvent;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import io.swagger.v3.oas.annotations.media.Schema;

/** An official calendar event as admins manage it, in any status, with its session and semester and its history. */
public record AdminCalendarEventResponse(
        @Schema(example = "14") Long eventId,
        @Schema(example = "2") Long academicSessionId,
        @Schema(example = "2026-27") String academicSessionCode,
        @Schema(description = "Null when the event applies across the session.", nullable = true, example = "9")
        Long semesterId,
        @Schema(nullable = true, example = "3") Integer semesterNumber,
        @Schema(description = "The program of that semester, or null for a session-wide event.", nullable = true,
                example = "BTECH-CSE")
        String programCode,
        @Schema(example = "Mid-semester examination week") String title,
        @Schema(nullable = true, example = "Examinations follow the published timetable.") String description,
        @Schema(allowableValues = {"ACADEMIC", "HOLIDAY", "EXAM", "REGISTRATION", "OTHER"}, example = "EXAM")
        String category,
        @Schema(example = "2026-10-20") LocalDate startDate,
        @Schema(description = "The last day, included.", example = "2026-10-25") LocalDate endDate,
        @Schema(allowableValues = {"DRAFT", "PUBLISHED", "CANCELLED"}, example = "PUBLISHED") String status,
        @Schema(description = "When the event was first published. It never changes, also after cancelling.",
                nullable = true, example = "2026-09-01T10:15:30Z")
        Instant publishedAt,
        @Schema(nullable = true, example = "2026-10-02T08:00:00Z") Instant cancelledAt,
        @Schema(example = "2026-08-28T09:00:00Z") Instant createdAt,
        @Schema(example = "2026-09-01T10:15:30Z") Instant updatedAt) {

    static AdminCalendarEventResponse of(AcademicCalendarEvent event) {
        AcademicSession session = event.getAcademicSession();
        Semester semester = event.getSemester();
        return new AdminCalendarEventResponse(event.getId(), session.getId(), session.getCode(),
                semester == null ? null : semester.getId(),
                semester == null ? null : semester.getSemesterNumber(),
                semester == null ? null : semester.getProgram().getCode(),
                event.getTitle(), event.getDescription(), event.getCategory().name(), event.getStartDate(),
                event.getEndDate(), event.getStatus().name(), event.getPublishedAt(), event.getCancelledAt(),
                event.getCreatedAt(), event.getUpdatedAt());
    }
}
