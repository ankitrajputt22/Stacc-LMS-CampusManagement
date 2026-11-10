package com.stacc.backend.engagement.tournaments;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.teams.CampusTeam;
import com.stacc.backend.engagement.teams.CampusTeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tournaments (v0.11.10). The campus office creates, publishes, and archives them, adds fixtures between our teams or
 * named external opponents, and records text scores with a result. Students and faculty read published tournaments.
 * Standings (played, won, drawn, lost, and points at 3 for a win and 1 for a draw) are worked out from played fixtures
 * on every read and never stored. Changes take the tournament row lock first, then the fixture's.
 */
@Service
@Transactional
public class TournamentService {

    static final String NOT_FOUND = "Tournament not found.";
    static final String FIXTURE_NOT_FOUND = "Match not found.";
    static final String TEAM_NOT_FOUND = "That team is not an active team.";

    public record Side(Long teamId, String name, boolean ours) {
    }

    public record FixtureView(Long fixtureId, Side home, Side away, Instant startsAt, String venue, CampusFixture.Status status,
            String homeScore, String awayScore, CampusFixture.Result result) {
    }

    public record Standing(String side, boolean ours, int played, int won, int drawn, int lost, int points) {
    }

    public record TournamentView(Long tournamentId, String name, String sport, String season, CampusTournament.Status status,
            List<FixtureView> fixtures, List<Standing> standings) {
    }

    public record TeamOption(Long teamId, String name, String season, String groupName) {
    }

    public record FixtureInput(Long homeTeamId, String homeName, Long awayTeamId, String awayName, Instant startsAt, String venue) {
    }

    private final CampusTournamentRepository tournaments;
    private final CampusFixtureRepository fixtures;
    private final CampusTeamRepository teams;
    private final Clock clock;

    public TournamentService(CampusTournamentRepository tournaments, CampusFixtureRepository fixtures, CampusTeamRepository teams, Clock clock) {
        this.tournaments = tournaments;
        this.fixtures = fixtures;
        this.teams = teams;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TournamentView> published() {
        return views(tournaments.findPublished());
    }

    @Transactional(readOnly = true)
    public List<TournamentView> all() {
        return views(tournaments.findAllForOffice());
    }

    /** Active teams of every sports group, to choose sides from. */
    @Transactional(readOnly = true)
    public List<TeamOption> teamOptions() {
        return teams.findActiveWithGroup().stream()
                .map(team -> new TeamOption(team.getId(), team.getName(), team.getSeason(), team.getGroup().getName())).toList();
    }

    public TournamentView create(Long adminId, String name, String sport, String season) {
        CampusTournament saved = tournaments.saveAndFlush(valid(() -> new CampusTournament(name, sport, season, adminId, now())));
        return view(saved, List.of());
    }

    public TournamentView publish(Long tournamentId) {
        CampusTournament tournament = locked(tournamentId);
        valid(() -> {
            tournament.publish(now());
            return tournament;
        });
        return reload(tournament);
    }

    public TournamentView archive(Long tournamentId) {
        CampusTournament tournament = locked(tournamentId);
        tournament.archive();
        return reload(tournament);
    }

    public TournamentView addFixture(Long tournamentId, FixtureInput input) {
        CampusTournament tournament = locked(tournamentId);
        CampusTeam home = activeTeam(input.homeTeamId());
        CampusTeam away = activeTeam(input.awayTeamId());
        fixtures.saveAndFlush(valid(() -> new CampusFixture(tournament, home, input.homeName(), away, input.awayName(), input.startsAt(), input.venue())));
        return reload(tournament);
    }

    /** Records or corrects a match's text scores and result. */
    public TournamentView record(Long adminId, Long fixtureId, String homeScore, String awayScore, CampusFixture.Result result) {
        CampusFixture fixture = lockedFixture(fixtureId);
        valid(() -> {
            fixture.record(homeScore, awayScore, result, adminId, now());
            return fixture;
        });
        fixtures.flush();
        return reload(fixture.getTournament());
    }

    public TournamentView cancel(Long fixtureId) {
        CampusFixture fixture = lockedFixture(fixtureId);
        valid(() -> {
            fixture.cancel();
            return fixture;
        });
        fixtures.flush();
        return reload(fixture.getTournament());
    }

    /** Standings from played fixtures: 3 points for a win, 1 for a draw; ordered by points, then wins, then name. */
    static List<Standing> standings(List<CampusFixture> played) {
        Map<String, int[]> table = new LinkedHashMap<>();
        Map<String, Boolean> ours = new LinkedHashMap<>();
        for (CampusFixture fixture : played) {
            if (fixture.getStatus() != CampusFixture.Status.PLAYED) {
                continue;
            }
            Side home = side(fixture.getHomeTeam(), fixture.getHomeName());
            Side away = side(fixture.getAwayTeam(), fixture.getAwayName());
            ours.putIfAbsent(home.name(), home.ours());
            ours.putIfAbsent(away.name(), away.ours());
            int[] h = table.computeIfAbsent(home.name(), key -> new int[4]);
            int[] a = table.computeIfAbsent(away.name(), key -> new int[4]);
            h[0]++;
            a[0]++;
            switch (fixture.getResult()) {
                case HOME_WIN -> { h[1]++; a[3]++; }
                case AWAY_WIN -> { a[1]++; h[3]++; }
                case DRAW -> { h[2]++; a[2]++; }
            }
        }
        return table.entrySet().stream()
                .map(entry -> new Standing(entry.getKey(), ours.get(entry.getKey()), entry.getValue()[0], entry.getValue()[1],
                        entry.getValue()[2], entry.getValue()[3], entry.getValue()[1] * 3 + entry.getValue()[2]))
                .sorted(Comparator.comparingInt(Standing::points).reversed().thenComparing(Comparator.comparingInt(Standing::won).reversed())
                        .thenComparing(Standing::side, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static Side side(CampusTeam team, String name) {
        return team == null ? new Side(null, name, false) : new Side(team.getId(), team.getName() + " (" + team.getSeason() + ")", true);
    }

    private List<TournamentView> views(List<CampusTournament> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, List<CampusFixture>> byTournament = fixtures.findForTournaments(found.stream().map(CampusTournament::getId).toList()).stream()
                .collect(Collectors.groupingBy(fixture -> fixture.getTournament().getId()));
        return found.stream().map(tournament -> view(tournament, byTournament.getOrDefault(tournament.getId(), List.of()))).toList();
    }

    private TournamentView reload(CampusTournament tournament) {
        return view(tournament, fixtures.findForTournaments(List.of(tournament.getId())));
    }

    private static TournamentView view(CampusTournament tournament, List<CampusFixture> found) {
        return new TournamentView(tournament.getId(), tournament.getName(), tournament.getSport(), tournament.getSeason(), tournament.getStatus(),
                found.stream().map(fixture -> new FixtureView(fixture.getId(), side(fixture.getHomeTeam(), fixture.getHomeName()),
                        side(fixture.getAwayTeam(), fixture.getAwayName()), fixture.getStartsAt(), fixture.getVenue(), fixture.getStatus(),
                        fixture.getHomeScore(), fixture.getAwayScore(), fixture.getResult())).toList(),
                standings(found));
    }

    private CampusTeam activeTeam(Long teamId) {
        if (teamId == null) {
            return null;
        }
        return teams.findById(teamId).filter(CampusTeam::isActive).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, TEAM_NOT_FOUND));
    }

    private CampusTournament locked(Long tournamentId) {
        return (tournamentId == null ? Optional.<CampusTournament>empty() : tournaments.findByIdForUpdate(tournamentId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    /** The tournament row first, then the fixture's, the same order as adding fixtures. */
    private CampusFixture lockedFixture(Long fixtureId) {
        Long tournamentId = (fixtureId == null ? Optional.<Long>empty() : fixtures.findTournamentIdOf(fixtureId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, FIXTURE_NOT_FOUND));
        locked(tournamentId);
        return fixtures.findByIdForUpdate(fixtureId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, FIXTURE_NOT_FOUND));
    }

    private static <T> T valid(Supplier<T> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (IllegalStateException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refused.getMessage());
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
