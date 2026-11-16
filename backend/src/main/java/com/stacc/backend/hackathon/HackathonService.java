package com.stacc.backend.hackathon;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hackathons (v0.12.12). Students form and join teams and hand in one submission per team while a published hackathon
 * has not ended; joining takes the team row lock and counts current members with a locking read, so a team never
 * passes the size limit. Named faculty judges score submissions after the end until results are released. Participants
 * never see scores before the release; then totals (the sum of the judges' scores) and places are worked out on read.
 */
@Service
@Transactional
public class HackathonService {

    static final String NOT_FOUND = "Hackathon not found.";
    static final String TEAM_NOT_FOUND = "Team not found.";
    static final String CLOSED = "This hackathon is not taking teams or work now.";
    static final String ON_A_TEAM = "You are already on a team in this hackathon.";
    static final String FULL = "This team is full.";

    public record SubmissionView(String title, String summary, String link, Instant submittedAt) {
    }

    public record TeamView(Long teamId, String name, List<String> memberLoginIds, boolean submitted, SubmissionView submission) {
    }

    public record ResultRow(int place, String teamName, String submissionTitle, int total) {
    }

    public record HackathonView(Long hackathonId, String title, String description, Instant startsAt, Instant endsAt, int maxTeamSize,
            Hackathon.Status status, boolean takingWork, Long myTeamId, List<TeamView> teams, List<ResultRow> results) {
    }

    public record JudgeItem(Long submissionId, String teamName, String title, String summary, String link, Integer myScore, String myComment) {
    }

    public record JudgeView(Long hackathonId, String title, Instant endsAt, boolean judging, List<JudgeItem> submissions) {
    }

    public record OfficeView(Long hackathonId, String title, Instant startsAt, Instant endsAt, int maxTeamSize, Hackathon.Status status,
            List<String> judgeLoginIds, int teams, List<ResultRow> standings) {
    }

    private final HackathonRepository hackathons;
    private final HackathonTeamRepository teams;
    private final HackathonTeamMemberRepository members;
    private final HackathonSubmissionRepository submissions;
    private final HackathonJudgeRepository judges;
    private final HackathonScoreRepository scores;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public HackathonService(HackathonRepository hackathons, HackathonTeamRepository teams, HackathonTeamMemberRepository members,
            HackathonSubmissionRepository submissions, HackathonJudgeRepository judges, HackathonScoreRepository scores,
            UserAccountRepository accounts, Clock clock) {
        this.hackathons = hackathons;
        this.teams = teams;
        this.members = members;
        this.submissions = submissions;
        this.judges = judges;
        this.scores = scores;
        this.accounts = accounts;
        this.clock = clock;
    }

    // --- Students

    @Transactional(readOnly = true)
    public List<HackathonView> visible(Long accountId) {
        List<Hackathon> found = hackathons.findVisible();
        if (found.isEmpty()) {
            return List.of();
        }
        Instant now = now();
        Map<Long, List<HackathonTeam>> byHackathon = teams.findForHackathons(found.stream().map(Hackathon::getId).toList()).stream()
                .collect(Collectors.groupingBy(team -> team.getHackathon().getId()));
        List<Long> teamIds = byHackathon.values().stream().flatMap(List::stream).map(HackathonTeam::getId).toList();
        Map<Long, List<HackathonTeamMember>> roster = teamIds.isEmpty() ? Map.of()
                : members.findCurrentFor(teamIds).stream().collect(Collectors.groupingBy(member -> member.getTeam().getId()));
        Map<Long, HackathonSubmission> work = teamIds.isEmpty() ? Map.of()
                : submissions.findForTeams(teamIds).stream().collect(Collectors.toMap(s -> s.getTeam().getId(), Function.identity()));
        return found.stream().map(hackathon -> {
            List<HackathonTeam> its = byHackathon.getOrDefault(hackathon.getId(), List.of());
            Long myTeamId = its.stream().filter(team -> roster.getOrDefault(team.getId(), List.of()).stream()
                    .anyMatch(member -> member.getAccount().getId().equals(accountId))).map(HackathonTeam::getId).findFirst().orElse(null);
            List<TeamView> shown = its.stream().map(team -> {
                HackathonSubmission submission = work.get(team.getId());
                boolean mine = team.getId().equals(myTeamId);
                return new TeamView(team.getId(), team.getName(),
                        roster.getOrDefault(team.getId(), List.of()).stream().map(member -> member.getAccount().getLoginId()).toList(),
                        submission != null, mine && submission != null ? submissionView(submission) : null);
            }).toList();
            List<ResultRow> results = hackathon.getStatus() == Hackathon.Status.RESULTS_RELEASED ? standings(its, work) : List.of();
            return new HackathonView(hackathon.getId(), hackathon.getTitle(), hackathon.getDescription(), hackathon.getStartsAt(),
                    hackathon.getEndsAt(), hackathon.getMaxTeamSize(), hackathon.getStatus(), hackathon.takingWork(now), myTeamId, shown, results);
        }).toList();
    }

    public void createTeam(Long accountId, Long hackathonId, String name) {
        Hackathon hackathon = lockedHackathon(hackathonId);
        if (!hackathon.takingWork(now())) {
            throw new ApiException(HttpStatus.CONFLICT, CLOSED);
        }
        if (members.findCurrent(hackathon.getId(), accountId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ON_A_TEAM);
        }
        HackathonTeam team;
        try {
            team = teams.saveAndFlush(valid(() -> new HackathonTeam(hackathon, name, now())));
        } catch (DataIntegrityViolationException taken) {
            throw new ApiException(HttpStatus.CONFLICT, "A team with this name already exists in this hackathon.");
        }
        join(team, accountId);
    }

    /** Joins a team that has a free place; the team row lock and a locking read of its members keep the size limit. */
    public void joinTeam(Long accountId, Long teamId) {
        HackathonTeam team = (teamId == null ? Optional.<HackathonTeam>empty() : teams.findByIdForUpdate(teamId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, TEAM_NOT_FOUND));
        if (!team.getHackathon().takingWork(now())) {
            throw new ApiException(HttpStatus.CONFLICT, CLOSED);
        }
        if (members.findCurrentForUpdate(team.getId()).size() >= team.getHackathon().getMaxTeamSize()) {
            throw new ApiException(HttpStatus.CONFLICT, FULL);
        }
        join(team, accountId);
    }

    public void leaveTeam(Long accountId, Long hackathonId) {
        Hackathon hackathon = lockedHackathon(hackathonId);
        if (!hackathon.takingWork(now())) {
            throw new ApiException(HttpStatus.CONFLICT, CLOSED);
        }
        HackathonTeamMember place = members.findCurrent(hackathon.getId(), accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, TEAM_NOT_FOUND));
        place.leave(now());
        members.flush();
    }

    /** Hands in or replaces the team's one submission, by any member, until the hackathon ends. */
    public void submit(Long accountId, Long hackathonId, String title, String summary, String link) {
        Long teamId = members.findCurrent(hackathonId, accountId).map(member -> member.getTeam().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Join or form a team first."));
        HackathonTeam team = teams.findByIdForUpdate(teamId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, TEAM_NOT_FOUND));
        if (!team.getHackathon().takingWork(now())) {
            throw new ApiException(HttpStatus.CONFLICT, CLOSED);
        }
        HackathonSubmission submission = submissions.findForTeam(teamId).orElseGet(() -> new HackathonSubmission(team));
        valid(() -> {
            submission.set(title, summary, link, now());
            return submission;
        });
        submissions.saveAndFlush(submission);
    }

    // --- Judges

    @Transactional(readOnly = true)
    public List<JudgeView> judging(Long accountId) {
        List<Long> ids = judges.findHackathonIdsFor(accountId);
        if (ids.isEmpty()) {
            return List.of();
        }
        Instant now = now();
        List<Hackathon> found = hackathons.findAllById(ids).stream().filter(h -> h.getStatus() != Hackathon.Status.DRAFT).toList();
        List<HackathonTeam> its = teams.findForHackathons(ids);
        List<HackathonSubmission> handedIn = its.isEmpty() ? List.of() : submissions.findForTeams(its.stream().map(HackathonTeam::getId).toList());
        Map<Long, HackathonScore> mine = handedIn.isEmpty() ? Map.of() : scores.findForSubmissions(handedIn.stream().map(HackathonSubmission::getId).toList())
                .stream().filter(score -> score.getJudge().getId().equals(accountId))
                .collect(Collectors.toMap(score -> score.getSubmission().getId(), Function.identity()));
        return found.stream().map(hackathon -> new JudgeView(hackathon.getId(), hackathon.getTitle(), hackathon.getEndsAt(), hackathon.judging(now),
                hackathon.judging(now) || hackathon.getStatus() == Hackathon.Status.RESULTS_RELEASED
                        ? handedIn.stream().filter(s -> s.getTeam().getHackathon().getId().equals(hackathon.getId())).map(s -> {
                            HackathonScore score = mine.get(s.getId());
                            return new JudgeItem(s.getId(), s.getTeam().getName(), s.getTitle(), s.getSummary(), s.getLink(),
                                    score == null ? null : score.getScore(), score == null ? null : score.getComment());
                        }).toList()
                        : List.of())).toList();
    }

    /** Gives or replaces a judge's score while judging is open. Anyone but the hackathon's judges gets 404. */
    public void score(Long accountId, Long submissionId, int value, String comment) {
        HackathonSubmission submission = (submissionId == null ? Optional.<HackathonSubmission>empty() : submissions.findById(submissionId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        Long hackathonId = submission.getTeam().getHackathon().getId();
        if (!judges.findHackathonIdsFor(accountId).contains(hackathonId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        Hackathon hackathon = lockedHackathon(hackathonId);
        if (!hackathon.judging(now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Scores are given after the hackathon ends and until results are released.");
        }
        HackathonScore score = scores.findOne(submissionId, accountId).orElseGet(() -> new HackathonScore(submission, accounts.getReferenceById(accountId)));
        valid(() -> {
            score.set(value, comment, now());
            return score;
        });
        try {
            scores.saveAndFlush(score);
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, "Your score was saved meanwhile; reload and try again.");
        }
    }

    // --- The campus office

    @Transactional(readOnly = true)
    public List<OfficeView> all() {
        List<Hackathon> found = hackathons.findAllForOffice();
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(Hackathon::getId).toList();
        Map<Long, List<HackathonTeam>> byHackathon = teams.findForHackathons(ids).stream().collect(Collectors.groupingBy(t -> t.getHackathon().getId()));
        List<Long> teamIds = byHackathon.values().stream().flatMap(List::stream).map(HackathonTeam::getId).toList();
        Map<Long, HackathonSubmission> work = teamIds.isEmpty() ? Map.of()
                : submissions.findForTeams(teamIds).stream().collect(Collectors.toMap(s -> s.getTeam().getId(), Function.identity()));
        Map<Long, List<String>> judgeIds = judges.findForHackathons(ids).stream().collect(Collectors.groupingBy(j -> j.getHackathon().getId(),
                Collectors.mapping(j -> j.getAccount().getLoginId(), Collectors.toList())));
        return found.stream().map(hackathon -> {
            List<HackathonTeam> its = byHackathon.getOrDefault(hackathon.getId(), List.of());
            return new OfficeView(hackathon.getId(), hackathon.getTitle(), hackathon.getStartsAt(), hackathon.getEndsAt(), hackathon.getMaxTeamSize(),
                    hackathon.getStatus(), judgeIds.getOrDefault(hackathon.getId(), List.of()), its.size(), standings(its, work));
        }).toList();
    }

    public void create(String title, String description, Instant startsAt, Instant endsAt, int maxTeamSize) {
        hackathons.saveAndFlush(valid(() -> new Hackathon(title, description, startsAt, endsAt, maxTeamSize, now())));
    }

    public void publish(Long hackathonId) {
        Hackathon hackathon = lockedHackathon(hackathonId);
        valid(() -> {
            hackathon.publish();
            return hackathon;
        });
    }

    public void addJudge(Long hackathonId, String loginId) {
        Hackathon hackathon = lockedHackathon(hackathonId);
        UserAccount faculty = (loginId == null ? Optional.<UserAccount>empty() : accounts.findWithRolesAndPermissionsByLoginId(loginId.strip()))
                .filter(found -> found.getStatus() == AccountStatus.ACTIVE && found.hasRole(RoleName.FACULTY))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Judges are active faculty accounts."));
        try {
            judges.saveAndFlush(new HackathonJudge(hackathon, faculty));
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, "That faculty member already judges this hackathon.");
        }
    }

    public void releaseResults(Long hackathonId) {
        Hackathon hackathon = lockedHackathon(hackathonId);
        valid(() -> {
            hackathon.releaseResults(now());
            return hackathon;
        });
    }

    public void archive(Long hackathonId) {
        lockedHackathon(hackathonId).archive();
    }

    /** Submitted teams by the sum of their judges' scores, highest first; equal totals share a place. */
    private List<ResultRow> standings(List<HackathonTeam> its, Map<Long, HackathonSubmission> work) {
        List<HackathonSubmission> handedIn = its.stream().map(team -> work.get(team.getId())).filter(s -> s != null).toList();
        if (handedIn.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> totals = scores.findForSubmissions(handedIn.stream().map(HackathonSubmission::getId).toList()).stream()
                .collect(Collectors.groupingBy(score -> score.getSubmission().getId(), Collectors.summingInt(HackathonScore::getScore)));
        List<HackathonSubmission> ordered = handedIn.stream()
                .sorted(Comparator.comparing((HackathonSubmission s) -> totals.getOrDefault(s.getId(), 0)).reversed()
                        .thenComparing(s -> s.getTeam().getName()))
                .toList();
        List<ResultRow> rows = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            HackathonSubmission s = ordered.get(index);
            int total = totals.getOrDefault(s.getId(), 0);
            int place = index > 0 && rows.get(index - 1).total() == total ? rows.get(index - 1).place() : index + 1;
            rows.add(new ResultRow(place, s.getTeam().getName(), s.getTitle(), total));
        }
        return rows;
    }

    private void join(HackathonTeam team, Long accountId) {
        try {
            members.saveAndFlush(new HackathonTeamMember(team, accounts.getReferenceById(accountId), now()));
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, ON_A_TEAM);
        }
    }

    private Hackathon lockedHackathon(Long hackathonId) {
        return (hackathonId == null ? Optional.<Hackathon>empty() : hackathons.findByIdForUpdate(hackathonId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private static SubmissionView submissionView(HackathonSubmission s) {
        return new SubmissionView(s.getTitle(), s.getSummary(), s.getLink(), s.getSubmittedAt());
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
