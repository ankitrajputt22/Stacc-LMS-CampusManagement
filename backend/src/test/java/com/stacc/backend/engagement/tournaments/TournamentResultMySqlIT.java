package com.stacc.backend.engagement.tournaments;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Recording a result and cancelling the same match at once never both succeed. A tournament of its own. */
class TournamentResultMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private TournamentService tournaments;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void aMatchIsEitherPlayedOrCancelled() throws Exception {
        Long admin = accounts.findByLoginId("admin1").orElseThrow().getId();
        Long tournamentId = tournaments.create(admin, "Race cup", "Football", "2030-31").tournamentId();
        Long fixtureId = tournaments.addFixture(tournamentId, new TournamentService.FixtureInput(null, "Hawks", null, "Owls",
                Instant.parse("2031-01-10T10:00:00Z"), null)).fixtures().getFirst().fixtureId();

        List<Concurrently.Outcome<TournamentService.TournamentView>> outcomes = Concurrently.run(
                () -> tournaments.record(admin, fixtureId, "2", "1", CampusFixture.Result.HOME_WIN),
                () -> tournaments.cancel(fixtureId));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        TournamentService.FixtureView after = tournaments.all().stream().filter(view -> view.tournamentId().equals(tournamentId))
                .findFirst().orElseThrow().fixtures().getFirst();
        assertThat(after.status()).isEqualTo(outcomes.getFirst().succeeded() ? CampusFixture.Status.PLAYED : CampusFixture.Status.CANCELLED);
    }
}
