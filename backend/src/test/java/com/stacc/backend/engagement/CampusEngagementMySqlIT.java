package com.stacc.backend.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.Concurrently.Outcome;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.engagement.events.CampusEventService;
import com.stacc.backend.engagement.events.CampusEventService.EventInput;
import com.stacc.backend.engagement.events.CampusEventService.EventView;
import com.stacc.backend.engagement.events.CampusEventService.Viewer;
import com.stacc.backend.engagement.facilities.CampusFacility;
import com.stacc.backend.engagement.facilities.CampusFacilityService;
import com.stacc.backend.engagement.facilities.CampusFacilityService.BookingView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The capacity and overlap rules hold under simultaneous requests: two students registering for the last place get one
 * place and one waitlist spot, and two bookings of the same time get one booking. Each test makes its own event or
 * facility, so no other test touches them.
 */
class CampusEngagementMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private CampusEventService events;

    @Autowired
    private CampusFacilityService facilities;

    @Autowired
    private UserAccountRepository accounts;

    private Long id(String loginId) {
        return accounts.findByLoginId(loginId).orElseThrow().getId();
    }

    @Test
    void twoRegistrationsForTheLastPlaceGiveOnePlaceAndOneWaitlistSpot() throws Exception {
        Viewer admin = new Viewer(id("admin1"), false, false, true);
        Instant start = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);
        EventView event = events.create(admin, new EventInput("Capacity test talk", null, null, "Hall", start, start.plus(Duration.ofHours(2)),
                1, null, null, true, false, false));
        events.publish(admin, event.eventId());

        List<Outcome<EventView>> outcomes = Concurrently.run(
                () -> events.register(new Viewer(id("student1"), true, false, false), event.eventId()),
                () -> events.register(new Viewer(id("student2"), true, false, false), event.eventId()));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        EventView after = events.one(admin, event.eventId());
        assertThat(after.registered()).isEqualTo(1);
        assertThat(after.waitlisted()).isEqualTo(1);
    }

    @Test
    void twoBookingsOfTheSameTimeGiveOneBooking() throws Exception {
        Long facilityId = facilities.create("IT-COURT", "Test court", CampusFacility.Kind.COURT, null, null, LocalTime.of(0, 0),
                LocalTime.of(23, 59), false).facilityId();
        Instant start = Instant.now().atZone(ZoneId.of("Asia/Kolkata")).plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0).toInstant();

        List<Outcome<BookingView>> outcomes = Concurrently.run(
                () -> facilities.book(id("student1"), facilityId, start, start.plus(Duration.ofHours(1)), "Practice"),
                () -> facilities.book(id("student2"), facilityId, start.plus(Duration.ofMinutes(30)), start.plus(Duration.ofHours(2)), "Match"));

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
        assertThat(facilities.upcoming(facilityId)).hasSize(1);
    }
}
