package com.stacc.backend.academic.admin;

import java.time.LocalDate;

import com.stacc.backend.academic.calendar.AcademicCalendarEvent;
import com.stacc.backend.academic.calendar.AcademicCalendarEventCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What an admin may send to create or edit an official calendar event. The event being edited comes from the route.
 * The session and semester are named by ID and loaded on the server. No request carries a status or a publication or
 * cancellation time: a new event is always a draft, and only the publish and cancel routes change its status.
 */
public final class AcademicCalendarRequests {

    private AcademicCalendarRequests() {
    }

    public record AcademicCalendarEventCreate(
            @Schema(description = "The academic session the event belongs to. It is loaded on the server.", example = "2")
            @NotNull(message = "Academic session is required")
            Long academicSessionId,

            @Schema(description = "Optional. A semester of that same session, for an event of that semester only. Null "
                    + "for an event across the whole session.", nullable = true, example = "9")
            Long semesterId,

            @Schema(description = "Plain text.", example = "Mid-semester examination week")
            @NotBlank(message = "Title is required")
            @Size(max = AcademicCalendarEvent.MAX_TITLE_LENGTH, message = "Title must be at most 200 characters")
            String title,

            @Schema(description = "Optional plain text, never treated as HTML.", nullable = true,
                    example = "Examinations follow the published timetable.")
            @Size(max = AcademicCalendarEvent.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 5000 characters")
            String description,

            @Schema(allowableValues = {"ACADEMIC", "HOLIDAY", "EXAM", "REGISTRATION", "OTHER"}, example = "EXAM")
            @NotNull(message = "Category is required")
            AcademicCalendarEventCategory category,

            @Schema(description = "The first day, a calendar date with no time, such as 2026-10-20.", example = "2026-10-20")
            @NotNull(message = "Start date is required")
            LocalDate startDate,

            @Schema(description = "The last day, included: the start date or later. The same day for a one-day event.",
                    example = "2026-10-25")
            @NotNull(message = "End date is required")
            LocalDate endDate) {
    }

    public record AcademicCalendarEventUpdate(
            @Schema(description = "A draft may move to another session. Once the event has been published, this must be "
                    + "its own session: a different one is refused with 409.", example = "2")
            @NotNull(message = "Academic session is required")
            Long academicSessionId,

            @Schema(description = "A semester of the session, or null for the whole session. Once the event has been "
                    + "published, this must be its own semester, or null if it has none: anything else is refused with "
                    + "409.", nullable = true, example = "9")
            Long semesterId,

            @Schema(description = "Plain text.", example = "Mid-semester examination week")
            @NotBlank(message = "Title is required")
            @Size(max = AcademicCalendarEvent.MAX_TITLE_LENGTH, message = "Title must be at most 200 characters")
            String title,

            @Schema(description = "Optional plain text, never treated as HTML. Null or blank removes it.", nullable = true,
                    example = "Examinations follow the published timetable.")
            @Size(max = AcademicCalendarEvent.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 5000 characters")
            String description,

            @Schema(allowableValues = {"ACADEMIC", "HOLIDAY", "EXAM", "REGISTRATION", "OTHER"}, example = "EXAM")
            @NotNull(message = "Category is required")
            AcademicCalendarEventCategory category,

            @Schema(example = "2026-10-20")
            @NotNull(message = "Start date is required")
            LocalDate startDate,

            @Schema(example = "2026-10-25")
            @NotNull(message = "End date is required")
            LocalDate endDate) {
    }
}
