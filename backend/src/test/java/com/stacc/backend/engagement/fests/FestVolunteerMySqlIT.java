package com.stacc.backend.engagement.fests;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Accepting two volunteers at once for a shift with one place accepts exactly one. A fest of its own. */
class FestVolunteerMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private FestService fests;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void aShiftNeverTakesMoreThanItsPlaces() throws Exception {
        Long admin = accounts.findByLoginId("admin1").orElseThrow().getId();
        Long first = accounts.findByLoginId("student1").orElseThrow().getId();
        Long second = accounts.findByLoginId("student2").orElseThrow().getId();
        Long festId = fests.create(admin, new FestService.FestInput("Race fest", null, LocalDate.of(2031, 1, 10), LocalDate.of(2031, 1, 11))).festId();
        fests.publish(festId);
        Instant start = Instant.parse("2031-01-10T04:00:00Z");
        Long shiftId = fests.addShift(festId, "Help desk", start, start.plusSeconds(7200), 1).shifts().getFirst().shiftId();
        fests.apply(first, shiftId, null);
        fests.apply(second, shiftId, null);
        List<Long> applications = fests.applications(festId).stream().map(FestService.ApplicationView::applicationId).toList();

        List<Concurrently.Outcome<FestService.ApplicationView>> outcomes = Concurrently.run(
                () -> fests.decide(applications.get(0), true),
                () -> fests.decide(applications.get(1), true));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(fests.applications(festId)).filteredOn(view -> view.status() == FestVolunteer.Status.ACCEPTED).hasSize(1);
    }
}
