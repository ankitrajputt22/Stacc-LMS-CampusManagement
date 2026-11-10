package com.stacc.backend.engagement.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.events.CampusEventService.Viewer;
import com.stacc.backend.pass.PassService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Attendance is recorded once, only for registered attendees, only near the event's time, and only by its managers. */
class EventAttendanceTest {

    private static final Instant START = Instant.parse("2026-11-20T10:00:00Z");
    private static final Viewer ORGANIZER = new Viewer(1L, false, true, false);

    private final CampusEventRepository events = mock(CampusEventRepository.class);
    private final CampusEventRegistrationRepository registrations = mock(CampusEventRegistrationRepository.class);
    private final CampusEventService eventService = mock(CampusEventService.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final CampusEvent event = mock(CampusEvent.class);

    private EventAttendanceService at(Instant now) {
        return new EventAttendanceService(events, registrations, eventService, mock(PassService.class), accounts, Clock.fixed(now, ZoneOffset.UTC));
    }

    private CampusEventRegistration registered() {
        UserAccount student = new UserAccount("student1", "hash");
        ReflectionTestUtils.setField(student, "id", 7L);
        CampusEventRegistration registration = mock(CampusEventRegistration.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(registration, "status", CampusEventRegistration.Status.REGISTERED);
        ReflectionTestUtils.setField(registration, "account", student);
        ReflectionTestUtils.setField(registration, "event", event);
        ReflectionTestUtils.setField(registration, "id", 40L);
        return registration;
    }

    private void organizedEvent() {
        when(event.getId()).thenReturn(3L);
        when(event.getStartsAt()).thenReturn(START);
        when(event.getEndsAt()).thenReturn(START.plusSeconds(7200));
        when(events.findById(3L)).thenReturn(Optional.of(event));
        when(eventService.canManage(event, ORGANIZER)).thenReturn(true);
        when(accounts.findByLoginId("student1")).thenAnswer(call -> Optional.of(registered().getAccount()));
    }

    @Test
    void anAttendeeIsCheckedInOnceWithinTheWindow() {
        organizedEvent();
        CampusEventRegistration registration = registered();
        when(registrations.findCurrentForAccount(3L, 7L)).thenReturn(Optional.of(registration));
        when(registrations.findByIdForUpdate(40L)).thenReturn(Optional.of(registration));
        when(accounts.getReferenceById(any())).thenReturn(new UserAccount("faculty1", "hash"));

        EventAttendanceService.CheckInResult first = at(START.minusSeconds(600)).checkInByLoginId(ORGANIZER, 3L, "student1");
        EventAttendanceService.CheckInResult again = at(START).checkInByLoginId(ORGANIZER, 3L, "student1");

        assertThat(first.alreadyCheckedIn()).isFalse();
        assertThat(again.alreadyCheckedIn()).isTrue();
        assertThat(registration.getAttendedAt()).isEqualTo(START.minusSeconds(600));
        assertThat(registration.getCheckInMethod()).isEqualTo(CampusEventRegistration.CheckInMethod.MANUAL);
    }

    @Test
    void checkInOutsideTheWindowOrByOthersIsRefused() {
        organizedEvent();
        CampusEventRegistration registration = registered();
        when(registrations.findCurrentForAccount(3L, 7L)).thenReturn(Optional.of(registration));
        when(registrations.findByIdForUpdate(40L)).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> at(START.minusSeconds(7200)).checkInByLoginId(ORGANIZER, 3L, "student1")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> at(START.plusSeconds(7200)).checkInByLoginId(ORGANIZER, 3L, "student1")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> at(START).checkInByLoginId(new Viewer(99L, true, false, false), 3L, "student1")).isInstanceOf(ApiException.class);

        ReflectionTestUtils.setField(registration, "status", CampusEventRegistration.Status.WAITLISTED);
        assertThatThrownBy(() -> registration.checkIn(CampusEventRegistration.CheckInMethod.MANUAL, null, START))
                .isInstanceOf(IllegalStateException.class);
    }
}
