package com.stacc.backend.engagement.fests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.auth.account.UserAccount;
import org.junit.jupiter.api.Test;

/** Fests and shifts keep their dates in order; an application is decided once and withdrawn only while live. */
class FestRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    @Test
    void aFestAndItsShiftsKeepTheirDatesInOrder() {
        CampusFest fest = new CampusFest("Tech Fest", null, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 1L, NOW);
        assertThatThrownBy(() -> fest.edit("Tech Fest", null, LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FestShift(fest, "Registration desk", NOW, NOW, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FestShift(fest, "Registration desk", NOW, NOW.plusSeconds(3600), 0)).isInstanceOf(IllegalArgumentException.class);
        fest.archive();
        assertThatThrownBy(() -> fest.schedule(4L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anApplicationIsDecidedOnceAndWithdrawnOnlyWhileLive() {
        FestVolunteer application = new FestVolunteer(mock(FestShift.class), mock(UserAccount.class), "I can help in the morning", NOW);
        application.decide(true, NOW);
        assertThat(application.getStatus()).isEqualTo(FestVolunteer.Status.ACCEPTED);
        assertThatThrownBy(() -> application.decide(false, NOW)).isInstanceOf(IllegalStateException.class);
        application.withdraw();
        assertThatThrownBy(application::withdraw).isInstanceOf(IllegalStateException.class);
    }
}
