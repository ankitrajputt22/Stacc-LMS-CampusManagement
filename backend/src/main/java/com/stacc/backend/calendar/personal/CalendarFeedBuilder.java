package com.stacc.backend.calendar.personal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.stacc.backend.academic.calendar.AcademicCalendarEventResponse;
import com.stacc.backend.calendar.MyCalendarResponse;

/** Writes a calendar as iCalendar text (RFC 5545): official events and personal entries as all-day, deadlines as moments. */
final class CalendarFeedBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final StringBuilder out = new StringBuilder();
    private final String stamp;

    private CalendarFeedBuilder(Instant now) {
        this.stamp = MOMENT.format(now);
        line("BEGIN:VCALENDAR");
        line("VERSION:2.0");
        line("PRODID:-//Stacc//Calendar//EN");
        line("X-WR-CALNAME:Stacc");
    }

    static String build(MyCalendarResponse calendar, List<PersonalCalendarEvent> personal, Instant now) {
        CalendarFeedBuilder feed = new CalendarFeedBuilder(now);
        for (AcademicCalendarEventResponse event : calendar.academicEvents()) {
            String title = "CANCELLED".equals(event.status()) ? "Cancelled: " + event.title() : event.title();
            feed.allDay("event-" + event.eventId(), title, event.startDate(), event.endDate());
        }
        calendar.studentAssignmentDeadlines().forEach(d -> feed.moment("deadline-" + d.assignmentId(), "Due: " + d.title() + " (" + d.courseCode() + ")", d.dueAt()));
        calendar.facultyAssignmentDeadlines().forEach(d -> feed.moment("teaching-" + d.assignmentId(), "Due: " + d.title() + " (" + d.courseCode() + ")", d.dueAt()));
        personal.forEach(e -> feed.allDay("personal-" + e.getId(), e.getTitle(), e.getStartDate(), e.getEndDate()));
        feed.line("END:VCALENDAR");
        return feed.out.toString();
    }

    private void allDay(String uid, String title, LocalDate start, LocalDate end) {
        line("BEGIN:VEVENT");
        line("UID:" + uid + "@stacc");
        line("DTSTAMP:" + stamp);
        line("DTSTART;VALUE=DATE:" + DATE.format(start));
        line("DTEND;VALUE=DATE:" + DATE.format(end.plusDays(1)));
        line("SUMMARY:" + escape(title));
        line("END:VEVENT");
    }

    private void moment(String uid, String title, Instant at) {
        line("BEGIN:VEVENT");
        line("UID:" + uid + "@stacc");
        line("DTSTAMP:" + stamp);
        line("DTSTART:" + MOMENT.format(at));
        line("DTEND:" + MOMENT.format(at));
        line("SUMMARY:" + escape(title));
        line("END:VEVENT");
    }

    // Text values escape backslashes, semicolons, commas, and line breaks.
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
    }

    private void line(String text) {
        out.append(text).append("\r\n");
    }
}
