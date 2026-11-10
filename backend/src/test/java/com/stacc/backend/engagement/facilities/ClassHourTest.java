package com.stacc.backend.engagement.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/** Class hours need a day, a time that ends after it starts, and a name; they block overlapping times only. */
class ClassHourTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    @Test
    void classHoursBlockOnlyOverlappingTimes() {
        CampusFacilityClassHour slot = new CampusFacilityClassHour(mock(CampusFacility.class), DayOfWeek.MONDAY, LocalTime.of(9, 0),
                LocalTime.of(11, 0), "CS101 lecture", NOW);
        assertThat(slot.overlaps(LocalTime.of(10, 0), LocalTime.of(12, 0))).isTrue();
        assertThat(slot.overlaps(LocalTime.of(8, 0), LocalTime.of(9, 0))).isFalse();
        assertThat(slot.overlaps(LocalTime.of(11, 0), LocalTime.of(12, 0))).isFalse();
        assertThatThrownBy(() -> new CampusFacilityClassHour(mock(CampusFacility.class), DayOfWeek.MONDAY, LocalTime.of(11, 0),
                LocalTime.of(9, 0), "Backwards", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampusFacilityClassHour(mock(CampusFacility.class), DayOfWeek.MONDAY, LocalTime.of(9, 0),
                LocalTime.of(10, 0), " ", NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
