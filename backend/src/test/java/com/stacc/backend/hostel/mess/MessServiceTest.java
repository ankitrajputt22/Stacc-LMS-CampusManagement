package com.stacc.backend.hostel.mess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.EngagementProperties;
import com.stacc.backend.hostel.Hostel;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelRepository;
import com.stacc.backend.hostel.HostelWardenService;
import org.junit.jupiter.api.Test;

/** A day's change replaces the weekly line; feedback averages show only for five ratings; other hostels answer 404. */
class MessServiceTest {

    // A Thursday at noon in Kolkata.
    private static final Instant NOW = Instant.parse("2026-11-12T06:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    private final MessMenuItemRepository items = mock(MessMenuItemRepository.class);
    private final MessMenuChangeRepository changes = mock(MessMenuChangeRepository.class);
    private final MealFeedbackRepository feedback = mock(MealFeedbackRepository.class);
    private final HostelRepository hostels = mock(HostelRepository.class);
    private final HostelWardenService wardens = mock(HostelWardenService.class);
    private final Hostel hostel = mock(Hostel.class);
    private final MessService service = new MessService(items, changes, feedback, hostels, mock(HostelAllocationRepository.class),
            wardens, new EngagementProperties("Asia/Kolkata"), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aDaysChangeReplacesTheWeeklyLineForThatDateOnly() {
        when(items.findAllForHostel(3L)).thenReturn(List.of(
                new MessMenuItem(hostel, 4, Meal.LUNCH, "Rice, dal, curd", 1L, NOW),
                new MessMenuItem(hostel, 5, Meal.LUNCH, "Pulao, raita", 1L, NOW)));
        when(changes.findBetween(eq(3L), any(), any())).thenReturn(List.of(
                new MessMenuChange(hostel, TODAY, Meal.LUNCH, "Festival thali", "Diwali", 1L, NOW)));

        List<MessService.DayMenu> week = service.week(3L, TODAY);

        assertThat(week).hasSize(7);
        assertThat(week.get(0).meals()).singleElement().satisfies(line -> {
            assertThat(line.items()).isEqualTo("Festival thali");
            assertThat(line.changed()).isTrue();
        });
        assertThat(week.get(1).meals()).singleElement().satisfies(line -> assertThat(line.items()).isEqualTo("Pulao, raita"));
        assertThat(week.get(7 - 1).date()).isEqualTo(TODAY.plusDays(6));
    }

    @Test
    void averagesNeedFiveRatingsAndOtherHostelsAreHidden() {
        when(wardens.isWardenOf(9L, 3L)).thenReturn(true);
        when(hostels.findById(3L)).thenReturn(Optional.of(hostel));
        List<MealFeedback> given = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            given.add(new MealFeedback(hostel, (long) i, TODAY, Meal.DINNER, i % 2 == 0 ? 4 : 5, null, NOW));
        }
        given.add(new MealFeedback(hostel, 7L, TODAY, Meal.LUNCH, 2, "Too salty", NOW));
        when(feedback.findForHostelBetween(eq(3L), any(), any())).thenReturn(given);

        MessService.ManagerMess mess = service.forManager(new MessService.Manager(9L, false), 3L, TODAY);

        assertThat(mess.lastMonth()).filteredOn(summary -> summary.meal() == Meal.DINNER).singleElement()
                .satisfies(summary -> assertThat(summary.average()).isEqualByComparingTo("4.4"));
        assertThat(mess.lastMonth()).filteredOn(summary -> summary.meal() == Meal.LUNCH).singleElement()
                .satisfies(summary -> {
                    assertThat(summary.ratings()).isEqualTo(1);
                    assertThat(summary.average()).isNull();
                });
        assertThat(mess.comments()).singleElement().satisfies(comment -> assertThat(comment.comment()).isEqualTo("Too salty"));
        assertThatThrownBy(() -> service.forManager(new MessService.Manager(9L, false), 4L, TODAY)).isInstanceOf(ApiException.class);
    }
}
