package com.stacc.backend.academic.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.admin.AcademicCalendarRequests.AcademicCalendarEventCreate;
import com.stacc.backend.academic.admin.AcademicCalendarRequests.AcademicCalendarEventUpdate;
import com.stacc.backend.academic.calendar.AcademicCalendarEvent;
import com.stacc.backend.academic.calendar.AcademicCalendarEventCategory;
import com.stacc.backend.academic.calendar.AcademicCalendarEventRepository;
import com.stacc.backend.academic.calendar.AcademicCalendarEventStatus;
import com.stacc.backend.academic.calendar.AcademicCalendarNotifications;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AcademicCalendarManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");
    private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);
    private static final LocalDate OCT_25 = LocalDate.of(2026, 10, 25);

    private final Department cse = MasterFixtures.department(3, "CSE", "Computer Science and Engineering");
    private final Program btech = MasterFixtures.program(4, cse, "BTECH-CSE", 8);
    private final AcademicSession sessionA = MasterFixtures.session(2, "2026-27", 2026);
    private final AcademicSession sessionB = MasterFixtures.session(5, "2027-28", 2027);
    private final Semester third = MasterFixtures.semester(9, btech, sessionA, 3);
    private final Semester fourth = MasterFixtures.semester(10, btech, sessionA, 4);
    private final Semester otherSession = MasterFixtures.semester(12, btech, sessionB, 5);

    private final AcademicCalendarEventRepository events = mock(AcademicCalendarEventRepository.class);
    private final AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final AcademicCalendarNotifications notifications = mock(AcademicCalendarNotifications.class);
    private final AcademicCalendarManagementService service =
            new AcademicCalendarManagementService(events, sessions, semesters, Clock.fixed(NOW, ZoneOffset.UTC),
                    notifications);

    @BeforeEach
    void setUp() {
        when(sessions.findById(2L)).thenReturn(Optional.of(sessionA));
        when(sessions.findById(5L)).thenReturn(Optional.of(sessionB));
        when(semesters.findWithContextById(9L)).thenReturn(Optional.of(third));
        when(semesters.findWithContextById(10L)).thenReturn(Optional.of(fourth));
        when(semesters.findWithContextById(12L)).thenReturn(Optional.of(otherSession));
        when(events.saveAndFlush(any())).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 14));
    }

    private static AcademicCalendarEventCreate create(Long sessionId, Long semesterId, LocalDate start, LocalDate end) {
        return new AcademicCalendarEventCreate(sessionId, semesterId, "Mid-semester examination week",
                "Examinations follow the published timetable.", AcademicCalendarEventCategory.EXAM, start, end);
    }

    private static AcademicCalendarEventUpdate update(Long sessionId, Long semesterId, String title, LocalDate start,
            LocalDate end) {
        return new AcademicCalendarEventUpdate(sessionId, semesterId, title, null, AcademicCalendarEventCategory.EXAM,
                start, end);
    }

    private AcademicCalendarEvent stored(Semester semester) {
        AcademicCalendarEvent event = MasterFixtures.withId(new AcademicCalendarEvent(sessionA, semester,
                "Mid-semester examination week", null, AcademicCalendarEventCategory.EXAM, OCT_20, OCT_25), 14);
        when(events.findByIdForUpdate(14L)).thenReturn(Optional.of(event));
        return event;
    }

    private static void assertRefused(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, refusal -> assertThat(refusal.getStatus()).isEqualTo(status))
                .hasMessage(message);
    }

    @Test
    void aNewEventIsAlwaysADraftOfTheSelectedSessionAndSemester() {
        AdminCalendarEventResponse saved = service.create(create(2L, 9L, OCT_20, OCT_25));

        assertThat(saved.eventId()).isEqualTo(14);
        assertThat(saved.status()).isEqualTo("DRAFT");
        assertThat(saved.publishedAt()).isNull();
        assertThat(saved.cancelledAt()).isNull();
        assertThat(saved.academicSessionCode()).isEqualTo("2026-27");
        assertThat(saved.semesterId()).isEqualTo(9);
        assertThat(saved.semesterNumber()).isEqualTo(3);
        assertThat(saved.programCode()).isEqualTo("BTECH-CSE");
        assertThat(saved.category()).isEqualTo("EXAM");
    }

    @Test
    void withoutASemesterTheEventCoversTheWholeSession() {
        AdminCalendarEventResponse saved = service.create(create(2L, null, OCT_20, OCT_20));

        assertThat(saved.semesterId()).isNull();
        assertThat(saved.semesterNumber()).isNull();
        assertThat(saved.startDate()).isEqualTo(saved.endDate());
        verify(semesters, never()).findWithContextById(any());
    }

    @Test
    void aSemesterOfAnotherSessionIsRefusedAndNothingIsSaved() {
        assertRefused(() -> service.create(create(2L, 12L, OCT_20, OCT_25)), HttpStatus.BAD_REQUEST,
                AcademicCalendarManagementService.SEMESTER_ELSEWHERE);
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void missingParentsAreNotFoundAndNothingIsSaved() {
        assertRefused(() -> service.create(create(99L, null, OCT_20, OCT_25)), HttpStatus.NOT_FOUND,
                AcademicCalendarManagementService.SESSION_MISSING);
        assertRefused(() -> service.create(create(2L, 99L, OCT_20, OCT_25)), HttpStatus.NOT_FOUND,
                AcademicCalendarManagementService.SEMESTER_MISSING);
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void anEndDateBeforeTheStartIsRefusedButOneDayAndSeveralDaysAreFine() {
        assertRefused(() -> service.create(create(2L, null, OCT_25, OCT_20)), HttpStatus.BAD_REQUEST,
                AcademicCalendarManagementService.DATES);
        assertRefused(() -> service.create(create(2L, null, LocalDate.of(1999, 1, 1), OCT_20)), HttpStatus.BAD_REQUEST,
                AcademicCalendarManagementService.DATES_SUPPORTED);
        verify(events, never()).saveAndFlush(any());

        assertThat(service.create(create(2L, null, OCT_20, OCT_20)).endDate()).isEqualTo(OCT_20);
        assertThat(service.create(create(2L, null, OCT_20, OCT_25)).endDate()).isEqualTo(OCT_25);
    }

    @Test
    void aDraftCanBeEditedInFullIncludingItsScope() {
        stored(third);

        AdminCalendarEventResponse saved = service.update(14L, update(5L, 12L, "Orientation", OCT_25, OCT_25));

        assertThat(saved.academicSessionId()).isEqualTo(5);
        assertThat(saved.semesterId()).isEqualTo(12);
        assertThat(saved.title()).isEqualTo("Orientation");
        assertThat(saved.status()).isEqualTo("DRAFT");
    }

    @Test
    void aDraftCannotMoveToASemesterOfAnotherSession() {
        stored(third);

        assertRefused(() -> service.update(14L, update(2L, 12L, "Orientation", OCT_20, OCT_25)), HttpStatus.BAD_REQUEST,
                AcademicCalendarManagementService.SEMESTER_ELSEWHERE);
    }

    @Test
    void aPublishedEventCanBeCorrectedButNotMoved() {
        AcademicCalendarEvent event = stored(third);
        event.publish(Instant.parse("2026-08-01T00:00:00Z"));

        AdminCalendarEventResponse saved = service.update(14L, update(2L, 9L, "Exam week (revised)", OCT_25, OCT_25.plusDays(2)));
        assertThat(saved.title()).isEqualTo("Exam week (revised)");
        assertThat(saved.startDate()).isEqualTo(OCT_25);
        assertThat(saved.status()).isEqualTo("PUBLISHED");
        assertThat(saved.publishedAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));

        assertRefused(() -> service.update(14L, update(5L, 9L, "Moved", OCT_20, OCT_25)), HttpStatus.CONFLICT,
                AcademicCalendarManagementService.SCOPE_FIXED);
        assertRefused(() -> service.update(14L, update(2L, 10L, "Moved", OCT_20, OCT_25)), HttpStatus.CONFLICT,
                AcademicCalendarManagementService.SCOPE_FIXED);
        assertRefused(() -> service.update(14L, update(2L, null, "Moved", OCT_20, OCT_25)), HttpStatus.CONFLICT,
                AcademicCalendarManagementService.SCOPE_FIXED);
        assertThat(event.getSemester()).isSameAs(third);
        assertThat(event.getTitle()).isEqualTo("Exam week (revised)");
    }

    @Test
    void aPublishedSessionWideEventStaysSessionWide() {
        AcademicCalendarEvent event = stored(null);
        event.publish(NOW);

        assertThat(service.update(14L, update(2L, null, "Diwali holiday", OCT_20, OCT_20)).title()).isEqualTo("Diwali holiday");
        assertRefused(() -> service.update(14L, update(2L, 9L, "Diwali holiday", OCT_20, OCT_20)), HttpStatus.CONFLICT,
                AcademicCalendarManagementService.SCOPE_FIXED);
    }

    @Test
    void aCancelledEventIsReadOnlyAndCannotBePublishedAgain() {
        AcademicCalendarEvent event = stored(third);
        event.publish(NOW);
        event.cancel(NOW);

        assertRefused(() -> service.update(14L, update(2L, 9L, "Changed", OCT_20, OCT_25)), HttpStatus.CONFLICT,
                AcademicCalendarManagementService.CANCELLED_EDIT);
        assertRefused(() -> service.publish(14L), HttpStatus.CONFLICT, AcademicCalendarManagementService.CANCELLED_PUBLISH);
        assertThat(event.getStatus()).isEqualTo(AcademicCalendarEventStatus.CANCELLED);
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void publishingTakesTheServerTimeOnceAndIsIdempotent() {
        AcademicCalendarEvent event = stored(third);

        AdminCalendarEventResponse published = service.publish(14L);
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedAt()).isEqualTo(NOW);

        AcademicCalendarManagementService later = new AcademicCalendarManagementService(events, sessions, semesters,
                Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC), notifications);
        assertThat(later.publish(14L).publishedAt()).isEqualTo(NOW);
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void cancellingKeepsThePublicationTimeAndIsIdempotent() {
        AcademicCalendarEvent event = stored(third);
        event.publish(Instant.parse("2026-08-01T00:00:00Z"));

        AdminCalendarEventResponse cancelled = service.cancel(14L);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelledAt()).isEqualTo(NOW);
        assertThat(cancelled.publishedAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));

        AcademicCalendarManagementService later = new AcademicCalendarManagementService(events, sessions, semesters,
                Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC), notifications);
        assertThat(later.cancel(14L).cancelledAt()).isEqualTo(NOW);
    }

    @Test
    void aDraftCanBeCancelledWithoutEverBeingPublished() {
        stored(null);

        AdminCalendarEventResponse cancelled = service.cancel(14L);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.publishedAt()).isNull();
    }

    @Test
    void unknownEventsAreNotFound() {
        when(events.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        when(events.findWithContextById(99L)).thenReturn(Optional.empty());

        assertRefused(() -> service.get(99L), HttpStatus.NOT_FOUND, AcademicCalendarManagementService.NOT_FOUND);
        assertRefused(() -> service.update(99L, update(2L, null, "x", OCT_20, OCT_20)), HttpStatus.NOT_FOUND,
                AcademicCalendarManagementService.NOT_FOUND);
        assertRefused(() -> service.publish(99L), HttpStatus.NOT_FOUND, AcademicCalendarManagementService.NOT_FOUND);
        assertRefused(() -> service.cancel(99L), HttpStatus.NOT_FOUND, AcademicCalendarManagementService.NOT_FOUND);
    }

    @Test
    void theListPassesItsFiltersAndRefusesReversedDates() {
        AcademicCalendarEvent draft = stored(third);
        when(events.findAllWithContext(2L, 9L, AcademicCalendarEventStatus.DRAFT, OCT_20, OCT_25)).thenReturn(List.of(draft));

        assertThat(service.list(OCT_20, OCT_25, 2L, 9L, AcademicCalendarEventStatus.DRAFT))
                .extracting(AdminCalendarEventResponse::eventId).containsExactly(14L);

        AcademicCalendarEventRepository untouched = mock(AcademicCalendarEventRepository.class);
        AcademicCalendarManagementService fresh = new AcademicCalendarManagementService(untouched, sessions, semesters,
                Clock.fixed(NOW, ZoneOffset.UTC), notifications);
        assertRefused(() -> fresh.list(OCT_25, OCT_20, null, null, null), HttpStatus.BAD_REQUEST,
                AcademicCalendarManagementService.FILTER_DATES);
        verifyNoInteractions(untouched);
    }

    @Test
    void publishingNotifiesOnceAndCancellingAPublishedEventNotifiesOnce() {
        AcademicCalendarEvent event = stored(third);

        service.publish(14L);
        service.publish(14L);
        verify(notifications, times(1)).published(event);

        service.cancel(14L);
        service.cancel(14L);
        verify(notifications, times(1)).cancelled(event);
    }

    @Test
    void draftsAndCancelledDraftsNotifyNobody() {
        stored(null);
        service.create(create(2L, null, OCT_20, OCT_20));
        service.update(14L, update(2L, null, "Changed", OCT_20, OCT_20));

        service.cancel(14L);

        verifyNoInteractions(notifications);
    }

    @Test
    void correctingAPublishedEventNotifiesNobodyAgain() {
        AcademicCalendarEvent event = stored(third);
        event.publish(NOW);

        service.update(14L, update(2L, 9L, "Exam week (revised)", OCT_25, OCT_25));

        verifyNoInteractions(notifications);
    }
}
