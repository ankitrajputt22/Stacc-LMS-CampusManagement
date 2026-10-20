package com.stacc.backend.academic.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class AcademicCalendarEventTest {

    private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);
    private static final LocalDate OCT_25 = LocalDate.of(2026, 10, 25);
    private static final Instant FIRST = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-09-05T10:00:00Z");

    private final CalendarFixtures college = new CalendarFixtures();

    private AcademicCalendarEvent draft() {
        return new AcademicCalendarEvent(college.session, college.third, "  Mid-semester examination week ",
                "Examinations follow the published timetable.", AcademicCalendarEventCategory.EXAM, OCT_20, OCT_25);
    }

    @Test
    void aNewEventIsADraftOfOneSessionWithNothingPublishedOrCancelledYet() {
        AcademicCalendarEvent event = draft();

        assertThat(event.getStatus()).isEqualTo(AcademicCalendarEventStatus.DRAFT);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getCancelledAt()).isNull();
        assertThat(event.getAcademicSession()).isSameAs(college.session);
        assertThat(event.getSemester()).isSameAs(college.third);
        assertThat(event.getTitle()).isEqualTo("Mid-semester examination week");
        assertThat(event.isVisibleToUsers()).isFalse();
    }

    @Test
    void theSessionIsRequiredAndTheSemesterIsOptional() {
        assertThatThrownBy(() -> new AcademicCalendarEvent(null, null, "Holiday", null,
                AcademicCalendarEventCategory.HOLIDAY, OCT_20, OCT_20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("academicSession");

        AcademicCalendarEvent sessionWide = new AcademicCalendarEvent(college.session, null, "Diwali holiday", "  ",
                AcademicCalendarEventCategory.HOLIDAY, OCT_20, OCT_20);
        assertThat(sessionWide.getSemester()).isNull();
        assertThat(sessionWide.getDescription()).isNull();
    }

    @Test
    void aSemesterMustBelongToTheEventsOwnSession() {
        assertThat(new AcademicCalendarEvent(college.session, college.fourth, "Semester 4 orientation", null,
                AcademicCalendarEventCategory.ACADEMIC, OCT_20, OCT_20).getSemester()).isSameAs(college.fourth);

        assertThatThrownBy(() -> new AcademicCalendarEvent(college.session, college.nextYearFifth, "Elsewhere", null,
                AcademicCalendarEventCategory.ACADEMIC, OCT_20, OCT_20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("semester must belong");
    }

    @Test
    void theTitleCategoryAndBothDatesAreRequired() {
        assertThatThrownBy(() -> new AcademicCalendarEvent(college.session, null, "   ", null,
                AcademicCalendarEventCategory.OTHER, OCT_20, OCT_20)).hasMessageContaining("title");
        assertThatThrownBy(() -> new AcademicCalendarEvent(college.session, null, "x".repeat(201), null,
                AcademicCalendarEventCategory.OTHER, OCT_20, OCT_20)).hasMessageContaining("at most 200");
        assertThatThrownBy(() -> new AcademicCalendarEvent(college.session, null, "Event", "x".repeat(5001),
                AcademicCalendarEventCategory.OTHER, OCT_20, OCT_20)).hasMessageContaining("at most 5000");
        assertThatThrownBy(() -> new AcademicCalendarEvent(college.session, null, "Event", null, null, OCT_20, OCT_20))
                .hasMessageContaining("category");
        assertThatThrownBy(() -> new AcademicCalendarEvent(college.session, null, "Event", null,
                AcademicCalendarEventCategory.OTHER, null, OCT_20)).hasMessageContaining("startDate");
        assertThatThrownBy(() -> new AcademicCalendarEvent(college.session, null, "Event", null,
                AcademicCalendarEventCategory.OTHER, OCT_20, null)).hasMessageContaining("endDate");
    }

    @Test
    void anEventLastsOneDayOrSeveralButNeverEndsBeforeItStarts() {
        AcademicCalendarEvent oneDay = new AcademicCalendarEvent(college.session, null, "Holiday", null,
                AcademicCalendarEventCategory.HOLIDAY, OCT_20, OCT_20);
        assertThat(oneDay.getEndDate()).isEqualTo(oneDay.getStartDate());

        AcademicCalendarEvent week = new AcademicCalendarEvent(college.session, null, "Exam week", null,
                AcademicCalendarEventCategory.EXAM, OCT_20, OCT_25);
        assertThat(week.getEndDate()).isEqualTo(OCT_25);

        assertThatThrownBy(() -> new AcademicCalendarEvent(college.session, null, "Backwards", null,
                AcademicCalendarEventCategory.EXAM, OCT_25, OCT_20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endDate must not be before startDate");
        assertThatThrownBy(() -> new AcademicCalendarEvent(college.session, null, "Too early", null,
                AcademicCalendarEventCategory.OTHER, LocalDate.of(1999, 12, 31), OCT_20))
                .hasMessageContaining("startDate must be between");
    }

    @Test
    void publishingRecordsTheFirstTimeAndPublishingAgainChangesNothing() {
        AcademicCalendarEvent event = draft();

        event.publish(FIRST);
        event.publish(LATER);

        assertThat(event.getStatus()).isEqualTo(AcademicCalendarEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(FIRST);
        assertThat(event.getCancelledAt()).isNull();
        assertThat(event.isVisibleToUsers()).isTrue();
    }

    @Test
    void cancellingAPublishedEventKeepsItVisibleAsCancelledAndCancellingAgainChangesNothing() {
        AcademicCalendarEvent event = draft();
        event.publish(FIRST);

        event.cancel(LATER);
        event.cancel(Instant.parse("2026-09-09T10:00:00Z"));

        assertThat(event.getStatus()).isEqualTo(AcademicCalendarEventStatus.CANCELLED);
        assertThat(event.getPublishedAt()).isEqualTo(FIRST);
        assertThat(event.getCancelledAt()).isEqualTo(LATER);
        assertThat(event.isVisibleToUsers()).isTrue();
    }

    @Test
    void publicationAndCancellationTimesAreKeptToTheMicrosecondAsStored() {
        AcademicCalendarEvent event = draft();

        event.publish(Instant.parse("2026-09-01T10:00:00.123456789Z"));
        event.cancel(Instant.parse("2026-09-02T10:00:00.987654321Z"));

        assertThat(event.getPublishedAt()).isEqualTo(Instant.parse("2026-09-01T10:00:00.123456Z"));
        assertThat(event.getCancelledAt()).isEqualTo(Instant.parse("2026-09-02T10:00:00.987654Z"));
    }

    @Test
    void aDraftCancelledBeforePublishingNeverBecomesVisible() {
        AcademicCalendarEvent event = draft();

        event.cancel(LATER);

        assertThat(event.getStatus()).isEqualTo(AcademicCalendarEventStatus.CANCELLED);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.isVisibleToUsers()).isFalse();
    }

    @Test
    void aCancelledEventCanNeitherBePublishedNorChanged() {
        AcademicCalendarEvent event = draft();
        event.publish(FIRST);
        event.cancel(LATER);

        assertThatThrownBy(() -> event.publish(Instant.parse("2026-10-01T00:00:00Z")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> event.edit("New title", null, AcademicCalendarEventCategory.EXAM, OCT_20, OCT_25))
                .isInstanceOf(IllegalStateException.class);
        assertThat(event.getStatus()).isEqualTo(AcademicCalendarEventStatus.CANCELLED);
        assertThat(event.getTitle()).isEqualTo("Mid-semester examination week");
    }

    @Test
    void aPublishedEventCanBeCorrectedButKeepsItsScopeAndPublicationTime() {
        AcademicCalendarEvent event = draft();
        event.publish(FIRST);

        event.edit("End-semester examination week", "", AcademicCalendarEventCategory.EXAM, OCT_25, OCT_25.plusDays(4));

        assertThat(event.getTitle()).isEqualTo("End-semester examination week");
        assertThat(event.getDescription()).isNull();
        assertThat(event.getStartDate()).isEqualTo(OCT_25);
        assertThat(event.getStatus()).isEqualTo(AcademicCalendarEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(FIRST);
        assertThatThrownBy(() -> event.rescope(college.session, college.fourth)).isInstanceOf(IllegalStateException.class);
        assertThat(event.getSemester()).isSameAs(college.third);
    }

    @Test
    void onlyADraftMovesToAnotherSessionOrSemester() {
        AcademicCalendarEvent event = draft();

        event.rescope(college.nextSession, college.nextYearFifth);
        assertThat(event.getAcademicSession()).isSameAs(college.nextSession);
        assertThat(event.getSemester()).isSameAs(college.nextYearFifth);

        event.rescope(college.session, null);
        assertThat(event.getSemester()).isNull();

        assertThatThrownBy(() -> event.rescope(college.session, college.nextYearFifth))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(event.getAcademicSession()).isSameAs(college.session);
    }
}
