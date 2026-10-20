package com.stacc.backend.calendar;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.academic.calendar.AcademicCalendarEventResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One account's calendar for a date range. Official events are dates; assignment deadlines are moments, chosen by
 * the requested time zone. Each deadline list is filled only for a role the account holds, and is empty otherwise.
 */
public record MyCalendarResponse(
        @Schema(example = "2026-10-01")
        LocalDate fromDate,

        @Schema(description = "The last day of the range, included.", example = "2026-10-31")
        LocalDate toDate,

        @Schema(description = "The IANA time zone the deadline range was worked out in.", example = "Asia/Kolkata")
        String timeZone,

        @Schema(description = "Published official events that apply to the account and overlap the range, and those "
                + "cancelled after they were published, marked CANCELLED: the earliest start first.")
        List<AcademicCalendarEventResponse> academicEvents,

        @Schema(description = "For a student: the published assignments of the LMS courses they may use now, due in the "
                + "range. Empty for other accounts.")
        List<StudentAssignmentDeadline> studentAssignmentDeadlines,

        @Schema(description = "For a faculty member: the draft and published assignments of the LMS courses they teach "
                + "and can open, due in the range. Empty for other accounts.")
        List<FacultyAssignmentDeadline> facultyAssignmentDeadlines) {

    /** A deadline of a student's own course. Nothing about submissions or grades is included. */
    public record StudentAssignmentDeadline(
            @Schema(example = "77") Long assignmentId,
            @Schema(example = "42") Long lmsCourseId,
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "Linked list exercises") String title,
            @Schema(description = "An absolute moment in UTC.", example = "2026-10-20T18:29:00Z") Instant dueAt,
            @Schema(example = "20.00") BigDecimal maxMarks) {
    }

    /** A deadline of a course the faculty member teaches, draft or published. */
    public record FacultyAssignmentDeadline(
            @Schema(example = "77") Long assignmentId,
            @Schema(example = "42") Long lmsCourseId,
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "Linked list exercises") String title,
            @Schema(description = "An absolute moment in UTC.", example = "2026-10-20T18:29:00Z") Instant dueAt,
            @Schema(example = "20.00") BigDecimal maxMarks,
            @Schema(allowableValues = {"DRAFT", "PUBLISHED"}, example = "DRAFT") String status) {
    }
}
