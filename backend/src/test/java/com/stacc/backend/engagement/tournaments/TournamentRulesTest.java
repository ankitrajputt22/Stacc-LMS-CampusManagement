package com.stacc.backend.engagement.tournaments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Standings come from played matches only; matches need two different sides and follow their status rules. */
class TournamentRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    private final CampusTournament tournament = new CampusTournament("Inter-college cup", "Football", "2026-27", 1L, NOW);

    private CampusFixture match(String home, String away, CampusFixture.Result result) {
        CampusFixture fixture = new CampusFixture(tournament, null, home, null, away, NOW, null);
        if (result != null) {
            fixture.record("2", "1", result, 1L, NOW);
        }
        return fixture;
    }

    @Test
    void standingsCountPlayedMatchesOnly() {
        List<TournamentService.Standing> table = TournamentService.standings(List.of(
                match("Hawks", "Owls", CampusFixture.Result.HOME_WIN),
                match("Owls", "Eagles", CampusFixture.Result.DRAW),
                match("Eagles", "Hawks", CampusFixture.Result.AWAY_WIN),
                match("Hawks", "Owls", null)));

        assertThat(table).extracting(TournamentService.Standing::side).containsExactly("Hawks", "Eagles", "Owls");
        assertThat(table.getFirst()).isEqualTo(new TournamentService.Standing("Hawks", false, 2, 2, 0, 0, 6));
        assertThat(table.get(1).points()).isEqualTo(1);
        assertThat(table.get(2)).isEqualTo(new TournamentService.Standing("Owls", false, 2, 0, 1, 1, 1));
    }

    @Test
    void aMatchNeedsTwoSidesAndFollowsItsStatus() {
        assertThatThrownBy(() -> match("Hawks", "hawks", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> match(" ", "Owls", null)).isInstanceOf(IllegalArgumentException.class);
        CampusFixture played = match("Hawks", "Owls", CampusFixture.Result.DRAW);
        assertThatThrownBy(played::cancel).isInstanceOf(IllegalStateException.class);
        CampusFixture cancelled = match("Hawks", "Owls", null);
        cancelled.cancel();
        assertThatThrownBy(() -> cancelled.record("1", "0", CampusFixture.Result.HOME_WIN, 1L, NOW)).isInstanceOf(IllegalStateException.class);
        tournament.archive();
        assertThatThrownBy(() -> match("Hawks", "Owls", null)).isInstanceOf(IllegalStateException.class);
    }
}
