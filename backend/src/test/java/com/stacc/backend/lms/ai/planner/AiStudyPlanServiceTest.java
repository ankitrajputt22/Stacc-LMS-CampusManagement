package com.stacc.backend.lms.ai.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.calendar.personal.PersonalCalendarEvent;
import com.stacc.backend.calendar.personal.PersonalCalendarService;
import com.stacc.backend.engagement.EngagementProperties;
import com.stacc.backend.lms.ai.flashcards.AiFlashcardStore;
import com.stacc.backend.lms.ai.mastery.AiMasteryService;
import com.stacc.backend.lms.ai.mastery.AiOpenAssignments;
import com.stacc.backend.lms.ai.mastery.Mastery;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiStudyPlanServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private final AiMasteryService mastery = mock(AiMasteryService.class);
    private final AiFlashcardStore flashcards = mock(AiFlashcardStore.class);
    private final AiOpenAssignments assignments = mock(AiOpenAssignments.class);
    private final PersonalCalendarService calendar = mock(PersonalCalendarService.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private AiStudyPlanService service;

    @BeforeEach
    void setUp() {
        Mastery.Result notStarted = new Mastery.Result(0, 0, Mastery.Level.NOT_STARTED, 0, null);
        when(mastery.view(1L, 3L)).thenReturn(List.of(new AiMasteryService.TopicMastery(31, "Joins", notStarted),
                new AiMasteryService.TopicMastery(32, "Indexes", notStarted)));
        LmsCourse course = mock(LmsCourse.class, RETURNS_DEEP_STUBS);
        when(course.getCourseOffering().getCourse().getCode()).thenReturn("CS201");
        when(lmsCourses.findById(3L)).thenReturn(Optional.of(course));
        service = new AiStudyPlanService(mastery, flashcards, assignments, calendar, mock(AiStudySessionRepository.class), lmsCourses,
                new EngagementProperties("UTC"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PersonalCalendarEvent entry(String title, LocalDate day) {
        return new PersonalCalendarEvent(1L, title, null, day, day, NOW);
    }

    @Test
    void planEntriesAreNotBusyDays() {
        List<StudyPlanner.Day> free = service.plan(1L, 3L, 7, 60);
        when(calendar.overlapping(eq(1L), any(), any())).thenReturn(List.of(entry("Study plan · CS201: 60 min", TODAY)));
        assertThat(service.plan(1L, 3L, 7, 60)).isEqualTo(free);
        when(calendar.overlapping(eq(1L), any(), any())).thenReturn(List.of(entry("Dentist", TODAY)));
        assertThat(service.plan(1L, 3L, 7, 60).getFirst().busy()).isTrue();
    }

    @Test
    void addingTwiceAddsNothingNew() {
        int added = service.addToCalendar(1L, 3L, 7, 60);
        assertThat(added).isPositive();
        verify(calendar).create(eq(1L), startsWith("Study plan · CS201: "), anyString(), eq(TODAY), eq(TODAY));

        List<PersonalCalendarEvent> existing = service.plan(1L, 3L, 7, 60).stream().filter(day -> !day.sessions().isEmpty())
                .map(day -> entry("Study plan · CS201: 60 min", day.date())).toList();
        when(calendar.overlapping(eq(1L), any(), any())).thenReturn(existing);
        assertThat(service.addToCalendar(1L, 3L, 7, 60)).isZero();
        verify(calendar, times(added)).create(eq(1L), anyString(), anyString(), any(), any());
    }
}
