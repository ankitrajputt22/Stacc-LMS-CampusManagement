package com.stacc.backend.search;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

import com.stacc.backend.academic.calendar.AcademicCalendarEvent;
import com.stacc.backend.academic.calendar.AcademicCalendarEventQueryService;
import com.stacc.backend.academic.calendar.AcademicCalendarEventRepository;
import com.stacc.backend.academic.calendar.AcademicCalendarEventStatus;
import com.stacc.backend.academic.calendar.AcademicCalendarScope;
import com.stacc.backend.academic.semester.Semester;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Official calendar events, with the relevance of {@code GET /api/calendar/me}: admins every event users may see;
 * students the events of their own enrolled or completed semesters ({@link AcademicCalendarEventQueryService#studentScope})
 * and faculty those of the semesters they teach ({@link AcademicCalendarEventQueryService#facultyScope}), each with the
 * session-wide events of those sessions. Drafts never match. Like the calendar, it needs ACADEMIC_CALENDAR_VIEW with
 * one of the three roles. Each event appears once, whichever roles make it relevant.
 */
@Component
public class CalendarSearchProvider implements SearchProvider {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AcademicCalendarEventQueryService eventQueries;
    private final AcademicCalendarEventRepository events;

    public CalendarSearchProvider(AcademicCalendarEventQueryService eventQueries, AcademicCalendarEventRepository events) {
        this.eventQueries = eventQueries;
        this.events = events;
    }

    @Override
    public Set<SearchResultType> types() {
        return Set.of(SearchResultType.ACADEMIC_CALENDAR_EVENT);
    }

    @Override
    public Optional<Page<SearchResult>> search(SearchResultType type, SearchContext context, Pageable page) {
        SearchActor actor = context.actor();
        if (type != SearchResultType.ACADEMIC_CALENDAR_EVENT || !actor.can("ACADEMIC_CALENDAR_VIEW")
                || !actor.holdsAnyCollegeRole()) {
            return Optional.empty();
        }
        SearchText text = context.text();
        if (actor.holds("ADMIN")) {
            return Optional.of(events.searchVisible(text.exact(), text.prefix(), text.contains(), page)
                    .map(CalendarSearchProvider::resultOf));
        }
        AcademicCalendarScope scope = AcademicCalendarScope.NONE;
        if (actor.holds("STUDENT")) {
            scope = scope.and(eventQueries.studentScope(actor.accountId()));
        }
        if (actor.holds("FACULTY")) {
            scope = scope.and(eventQueries.facultyScope(actor.accountId()));
        }
        // Both sets come from the same semesters, so neither is empty unless both are; no empty IN list is sent.
        if (scope.semesterIds().isEmpty() || scope.academicSessionIds().isEmpty()) {
            return Optional.of(Page.empty(page));
        }
        return Optional.of(events.searchVisibleForScope(scope.semesterIds(), scope.academicSessionIds(), text.exact(),
                text.prefix(), text.contains(), page).map(CalendarSearchProvider::resultOf));
    }

    /** Opens the calendar on the event's first month. */
    static SearchResult resultOf(AcademicCalendarEvent event) {
        Semester semester = event.getSemester();
        return new SearchResult(SearchResultType.ACADEMIC_CALENDAR_EVENT, event.getTitle(),
                SearchFormat.join(SearchFormat.days(event.getStartDate(), event.getEndDate()),
                        SearchFormat.words(event.getCategory()), event.getAcademicSession().getCode(),
                        semester == null ? null : semester.getProgram().getCode() + " Semester " + semester.getSemesterNumber(),
                        event.getStatus() == AcademicCalendarEventStatus.CANCELLED ? "Cancelled" : null),
                SearchFormat.snippet(event.getDescription()), "/calendar?month=" + MONTH.format(event.getStartDate()),
                null);
    }
}
