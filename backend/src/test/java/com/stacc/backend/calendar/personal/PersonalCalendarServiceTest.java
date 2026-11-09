package com.stacc.backend.calendar.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.calendar.MyCalendarResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PersonalCalendarServiceTest {

    private final PersonalCalendarEventRepository events = mock(PersonalCalendarEventRepository.class);
    private final CalendarFeedLinkRepository links = mock(CalendarFeedLinkRepository.class);
    private final PersonalCalendarService service = new PersonalCalendarService(events, links,
            Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void aNewLinkStoresOnlyTheFingerprintAndRevokesTheOldOne() {
        CalendarFeedLink old = new CalendarFeedLink(1L, "a".repeat(64), Instant.parse("2026-11-01T00:00:00Z"));
        when(links.findActiveForUpdate(1L)).thenReturn(Optional.of(old));
        PersonalCalendarService.NewFeed feed = service.createFeed(1L);

        assertThat(old.isActive()).isFalse();
        ArgumentCaptor<CalendarFeedLink> saved = ArgumentCaptor.forClass(CalendarFeedLink.class);
        verify(links, org.mockito.Mockito.times(2)).saveAndFlush(saved.capture());
        CalendarFeedLink created = saved.getAllValues().get(1);
        assertThat(feed.token()).hasSize(43);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(created, "tokenFingerprint"))
                .isEqualTo(PersonalCalendarService.fingerprint(feed.token()))
                .isNotEqualTo(feed.token());
    }

    @Test
    void aMalformedSecretIsNeverLookedUp() {
        assertThat(service.accountForFeed("../../etc")).isEmpty();
        verify(links, never()).findActiveByFingerprint(any());
    }

    @Test
    void theFeedEscapesTextAndListsPersonalEntriesAsAllDay() {
        PersonalCalendarEvent entry = new PersonalCalendarEvent(1L, "Study, group; meet", null,
                LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-12"), Instant.now());
        String ics = CalendarFeedBuilder.build(new MyCalendarResponse(LocalDate.MIN, LocalDate.MAX, "UTC", List.of(), List.of(), List.of()),
                List.of(entry), Instant.parse("2026-11-10T10:00:00Z"));
        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").contains("SUMMARY:Study\\, group\\; meet")
                .contains("DTSTART;VALUE=DATE:20261112").contains("DTEND;VALUE=DATE:20261113").endsWith("END:VCALENDAR\r\n");
    }
}
