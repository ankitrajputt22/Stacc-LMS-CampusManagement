package com.stacc.backend.hackathon;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Two students joining a team's last place at once give one place. A hackathon of its own. */
class HackathonMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private HackathonService hackathons;

    @Autowired
    private HackathonRepository repository;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void aTeamNeverPassesItsSize() throws Exception {
        Long founder = accounts.findByLoginId("student1").orElseThrow().getId();
        Long first = accounts.findByLoginId("student2").orElseThrow().getId();
        Long second = accounts.findByLoginId("faculty1").orElseThrow().getId();
        Instant start = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS);
        hackathons.create("Race sprint", null, start, start.plus(Duration.ofDays(2)), 2);
        Long hackathonId = repository.findAllForOffice().stream().filter(h -> h.getTitle().equals("Race sprint")).findFirst().orElseThrow().getId();
        hackathons.publish(hackathonId);
        hackathons.createTeam(founder, hackathonId, "Racers");
        Long teamId = hackathons.visible(founder).stream().filter(view -> view.hackathonId().equals(hackathonId)).findFirst().orElseThrow().myTeamId();

        List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                () -> {
                    hackathons.joinTeam(first, teamId);
                    return null;
                },
                () -> {
                    hackathons.joinTeam(second, teamId);
                    return null;
                });

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(hackathons.visible(founder).stream().filter(view -> view.hackathonId().equals(hackathonId)).findFirst().orElseThrow()
                .teams().getFirst().memberLoginIds()).hasSize(2);
    }
}
