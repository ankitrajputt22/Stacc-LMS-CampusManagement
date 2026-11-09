package com.stacc.backend.academic.calendar;

import java.time.LocalDate;

import com.stacc.backend.academic.semester.Semester;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An official calendar event as students, faculty, and admins see it on their calendar: what it is, when, and which
 * session and semester it belongs to, by code and number. Draft events are never sent this way.
 */
public record AcademicCalendarEventResponse(
        @Schema(example = "14")
        Long eventId,

        @Schema(example = "Mid-semester examination week")
        String title,

        @Schema(description = "Plain text, or null when none was entered.", nullable = true,
                example = "Examinations follow the published timetable.")
        String description,

        @Schema(allowableValues = {"ACADEMIC", "HOLIDAY", "EXAM", "REGISTRATION", "OTHER"}, example = "EXAM")
        String category,

        @Schema(description = "The first day, a calendar date with no time or time zone.", example = "2026-10-20")
        LocalDate startDate,

        @Schema(description = "The last day, included. The same as the start date for a one-day event.",
                example = "2026-10-25")
        LocalDate endDate,

        @Schema(description = "PUBLISHED, or CANCELLED for an event that was published and then called off. It is still "
                + "shown so it does not silently disappear.", allowableValues = {"PUBLISHED", "CANCELLED"},
                example = "PUBLISHED")
        String status,

        @Schema(example = "2026-27")
        String academicSessionCode,

        @Schema(description = "The semester the event applies to, or null when it applies across the session.",
                nullable = true, example = "3")
        Integer semesterNumber,

        @Schema(description = "The program of that semester, or null for a session-wide event.", nullable = true,
                example = "BTECH-CSE")
        String programCode) {

    public static AcademicCalendarEventResponse of(AcademicCalendarEvent event) {
        Semester semester = event.getSemester();
        return new AcademicCalendarEventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCategory().name(),
                event.getStartDate(),
                event.getEndDate(),
                event.getStatus().name(),
                event.getAcademicSession().getCode(),
                semester == null ? null : semester.getSemesterNumber(),
                semester == null ? null : semester.getProgram().getCode());
    }
}
