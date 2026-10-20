package com.stacc.backend.academic.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.stacc.backend.academic.admin.AcademicCalendarRequests.AcademicCalendarEventCreate;
import com.stacc.backend.academic.admin.AcademicCalendarRequests.AcademicCalendarEventUpdate;
import com.stacc.backend.academic.calendar.AcademicCalendarEvent;
import com.stacc.backend.academic.calendar.AcademicCalendarEventRepository;
import com.stacc.backend.academic.calendar.AcademicCalendarEventStatus;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins list, create, edit, publish, and cancel official calendar events. An event belongs to an academic session
 * and, optionally, to one semester of that same session, both loaded on the server from the IDs sent. A new event is
 * a draft. A draft can be edited in full; once published, its session and semester are fixed and only its title,
 * description, category, and dates can be corrected; a cancelled event cannot be changed at all. Publishing and
 * cancelling record their time from the server's clock. Nothing is ever restored or deleted, and nothing is sent to
 * anyone: there are no notifications.
 */
@Service
@Transactional
public class AcademicCalendarManagementService {

    static final String NOT_FOUND = "Calendar event not found.";
    static final String SESSION_MISSING = "The selected academic session does not exist.";
    static final String SEMESTER_MISSING = "The selected semester does not exist.";
    static final String SEMESTER_ELSEWHERE = "The selected semester is not part of the selected academic session.";
    static final String DATES = "The end date must not be before the start date.";
    static final String DATES_SUPPORTED = "Dates must be between " + AcademicCalendarEvent.EARLIEST_DATE + " and "
            + AcademicCalendarEvent.LATEST_DATE + ".";
    static final String FILTER_DATES = "The from date must not be after the to date.";
    static final String SCOPE_FIXED = "The academic session and semester of a published event cannot be changed. "
            + "Cancel it and create a new event instead.";
    static final String CANCELLED_EDIT = "A cancelled event cannot be changed.";
    static final String CANCELLED_PUBLISH = "A cancelled event cannot be published again.";

    private final AcademicCalendarEventRepository events;
    private final AcademicSessionRepository sessions;
    private final SemesterRepository semesters;
    private final Clock clock;

    public AcademicCalendarManagementService(AcademicCalendarEventRepository events, AcademicSessionRepository sessions,
            SemesterRepository semesters, Clock clock) {
        this.events = events;
        this.sessions = sessions;
        this.semesters = semesters;
        this.clock = clock;
    }

    /**
     * Events in every status, narrowed by each filter that is not null, the earliest start first. The dates keep the
     * events that overlap them; a semester keeps the events of exactly that semester.
     */
    @Transactional(readOnly = true)
    public List<AdminCalendarEventResponse> list(LocalDate from, LocalDate to, Long academicSessionId, Long semesterId,
            AcademicCalendarEventStatus status) {
        if (from != null && to != null && from.isAfter(to)) {
            throw AcademicMasterErrors.badRequest(FILTER_DATES);
        }
        return events.findAllWithContext(academicSessionId, semesterId, status, from, to).stream()
                .map(AdminCalendarEventResponse::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminCalendarEventResponse get(Long eventId) {
        return events.findWithContextById(eventId)
                .map(AdminCalendarEventResponse::of)
                .orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
    }

    /** Creates a draft. Nothing else about its status can be sent. */
    public AdminCalendarEventResponse create(AcademicCalendarEventCreate request) {
        requireDates(request.startDate(), request.endDate());
        AcademicSession session = findSession(request.academicSessionId());
        Semester semester = findSemesterOf(request.semesterId(), session);
        AcademicCalendarEvent event = new AcademicCalendarEvent(session, semester, request.title(), request.description(),
                request.category(), request.startDate(), request.endDate());
        return AdminCalendarEventResponse.of(events.saveAndFlush(event));
    }

    public AdminCalendarEventResponse update(Long eventId, AcademicCalendarEventUpdate request) {
        AcademicCalendarEvent event = findForUpdate(eventId);
        if (event.isCancelled()) {
            throw AcademicMasterErrors.conflict(CANCELLED_EDIT);
        }
        requireDates(request.startDate(), request.endDate());
        if (event.getStatus() == AcademicCalendarEventStatus.DRAFT) {
            AcademicSession session = findSession(request.academicSessionId());
            event.rescope(session, findSemesterOf(request.semesterId(), session));
        } else if (!request.academicSessionId().equals(event.getAcademicSession().getId())
                || !Objects.equals(request.semesterId(), event.getSemester() == null ? null : event.getSemester().getId())) {
            // Moving a published event would silently take it off some calendars and put it on others.
            throw AcademicMasterErrors.conflict(SCOPE_FIXED);
        }
        event.edit(request.title(), request.description(), request.category(), request.startDate(), request.endDate());
        return AdminCalendarEventResponse.of(events.saveAndFlush(event));
    }

    /** Publishes a draft. An already published event stays as it is; a cancelled one is refused. */
    public AdminCalendarEventResponse publish(Long eventId) {
        AcademicCalendarEvent event = findForUpdate(eventId);
        if (event.isCancelled()) {
            throw AcademicMasterErrors.conflict(CANCELLED_PUBLISH);
        }
        event.publish(clock.instant());
        return AdminCalendarEventResponse.of(events.saveAndFlush(event));
    }

    /** Cancels a draft or a published event. An already cancelled event stays as it is. */
    public AdminCalendarEventResponse cancel(Long eventId) {
        AcademicCalendarEvent event = findForUpdate(eventId);
        event.cancel(clock.instant());
        return AdminCalendarEventResponse.of(events.saveAndFlush(event));
    }

    private static void requireDates(LocalDate startDate, LocalDate endDate) {
        if (outsideSupported(startDate) || outsideSupported(endDate)) {
            throw AcademicMasterErrors.badRequest(DATES_SUPPORTED);
        }
        if (endDate.isBefore(startDate)) {
            throw AcademicMasterErrors.badRequest(DATES);
        }
    }

    private static boolean outsideSupported(LocalDate date) {
        return date.isBefore(AcademicCalendarEvent.EARLIEST_DATE) || date.isAfter(AcademicCalendarEvent.LATEST_DATE);
    }

    private AcademicSession findSession(Long academicSessionId) {
        return sessions.findById(academicSessionId).orElseThrow(() -> AcademicMasterErrors.notFound(SESSION_MISSING));
    }

    /** The semester named, or null for a session-wide event. It must be a semester of the given session. */
    private Semester findSemesterOf(Long semesterId, AcademicSession session) {
        if (semesterId == null) {
            return null;
        }
        Semester semester = semesters.findWithContextById(semesterId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(SEMESTER_MISSING));
        if (!AcademicCalendarEvent.belongsToSession(semester, session)) {
            throw AcademicMasterErrors.badRequest(SEMESTER_ELSEWHERE);
        }
        return semester;
    }

    private AcademicCalendarEvent findForUpdate(Long eventId) {
        return events.findByIdForUpdate(eventId).orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
    }
}
