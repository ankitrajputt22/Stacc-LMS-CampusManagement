package com.stacc.backend.engagement.teams;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.groups.CampusGroup;
import com.stacc.backend.engagement.groups.CampusGroupMembership;
import com.stacc.backend.engagement.groups.CampusGroupMembershipRepository;
import com.stacc.backend.engagement.groups.CampusGroupOfficerRepository;
import com.stacc.backend.engagement.groups.CampusGroupRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teams of sports groups (v0.11.9): one team per name and season in a group, a roster of the group's active members,
 * and at most one current captain and vice-captain. The group's officers and the campus office manage teams under the
 * team row lock, reading the roster with a locking read; active members read them. Anyone else gets the same 404 as
 * for a missing group. Players leave the roster but are never deleted, and teams are made inactive.
 */
@Service
@Transactional
public class TeamService {

    static final String GROUP_NOT_FOUND = "Group not found.";
    static final String TEAM_NOT_FOUND = "Team not found.";
    static final String PLAYER_NOT_FOUND = "Player not found.";
    static final String NOT_MEMBER = "Only active members of the group can play on its teams.";
    static final String ON_TEAM = "That member is already on the team.";
    static final String INACTIVE = "This team is inactive.";
    static final String TAKEN = "A team with this name already exists for that season.";

    public record PlayerView(Long playerId, String loginId, CampusTeamMember.Role role, Instant joinedAt) {
    }

    public record TeamView(Long teamId, String name, String season, boolean active, List<PlayerView> players) {
    }

    public record GroupTeams(boolean canManage, List<TeamView> teams) {
    }

    private static final Comparator<CampusTeamMember> ROSTER = Comparator
            .comparing((CampusTeamMember member) -> switch (member.getRole()) {
                case CAPTAIN -> 0;
                case VICE_CAPTAIN -> 1;
                case PLAYER -> 2;
            })
            .thenComparing(member -> member.getAccount().getLoginId());

    private final CampusTeamRepository teams;
    private final CampusTeamMemberRepository players;
    private final CampusGroupRepository groups;
    private final CampusGroupMembershipRepository memberships;
    private final CampusGroupOfficerRepository officers;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public TeamService(CampusTeamRepository teams, CampusTeamMemberRepository players, CampusGroupRepository groups,
            CampusGroupMembershipRepository memberships, CampusGroupOfficerRepository officers, UserAccountRepository accounts,
            Clock clock) {
        this.teams = teams;
        this.players = players;
        this.groups = groups;
        this.memberships = memberships;
        this.officers = officers;
        this.accounts = accounts;
        this.clock = clock;
    }

    /** A group's teams with their current rosters, for active members, officers, and the campus office. */
    @Transactional(readOnly = true)
    public GroupTeams forGroup(Long accountId, boolean office, Long groupId) {
        boolean manager = groupId != null && (office || officers.isOfficer(groupId, accountId));
        if (!manager && !isActiveMember(accountId, groupId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, GROUP_NOT_FOUND);
        }
        List<CampusTeam> found = teams.findForGroup(groupId);
        Map<Long, List<CampusTeamMember>> rosters = found.isEmpty() ? Map.of()
                : players.findCurrentForTeams(found.stream().map(CampusTeam::getId).toList()).stream()
                        .collect(Collectors.groupingBy(member -> member.getTeam().getId()));
        return new GroupTeams(manager, found.stream().map(team -> view(team, rosters.getOrDefault(team.getId(), List.of()))).toList());
    }

    public TeamView create(Long accountId, boolean office, Long groupId, String name, String season) {
        CampusGroup group = managedGroup(accountId, office, groupId);
        try {
            return view(teams.saveAndFlush(valid(() -> new CampusTeam(group, name, season, accountId, now()))), List.of());
        } catch (DataIntegrityViolationException taken) {
            throw new ApiException(HttpStatus.CONFLICT, TAKEN);
        }
    }

    public TeamView deactivate(Long accountId, boolean office, Long teamId) {
        CampusTeam team = managedTeam(accountId, office, teamId);
        team.deactivate();
        return view(team, players.findCurrentForUpdate(team.getId()));
    }

    /** Adds an active member of the group to the roster; naming them captain or vice-captain hands that role over. */
    public TeamView addPlayer(Long accountId, boolean office, Long teamId, String loginId, CampusTeamMember.Role role) {
        CampusTeam team = openTeam(accountId, office, teamId);
        UserAccount account = (loginId == null ? Optional.<UserAccount>empty() : accounts.findByLoginId(loginId.strip()))
                .filter(found -> isActiveMember(found.getId(), team.getGroup().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_MEMBER));
        List<CampusTeamMember> roster = players.findCurrentForUpdate(team.getId());
        if (roster.stream().anyMatch(member -> member.getAccount().getId().equals(account.getId()))) {
            throw new ApiException(HttpStatus.CONFLICT, ON_TEAM);
        }
        handOver(roster, role);
        players.saveAndFlush(new CampusTeamMember(team, account, role, now()));
        return view(team, players.findCurrentForUpdate(team.getId()));
    }

    /** Changes a player's role; naming a new captain or vice-captain makes the previous one a player. */
    public TeamView changeRole(Long accountId, boolean office, Long playerId, CampusTeamMember.Role role) {
        CampusTeam team = openTeam(accountId, office, teamOf(playerId));
        List<CampusTeamMember> roster = players.findCurrentForUpdate(team.getId());
        CampusTeamMember player = roster.stream().filter(member -> member.getId().equals(playerId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PLAYER_NOT_FOUND));
        if (player.getRole() != role) {
            handOver(roster, role);
            player.changeRole(role);
            players.flush();
        }
        return view(team, roster);
    }

    /** Takes a player off the roster; the row stays as history. */
    public TeamView removePlayer(Long accountId, boolean office, Long playerId) {
        CampusTeam team = openTeam(accountId, office, teamOf(playerId));
        List<CampusTeamMember> roster = players.findCurrentForUpdate(team.getId());
        CampusTeamMember player = roster.stream().filter(member -> member.getId().equals(playerId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PLAYER_NOT_FOUND));
        player.leave(now());
        players.flush();
        return view(team, roster.stream().filter(member -> member.getLeftAt() == null).toList());
    }

    /** The current holder of a captain or vice-captain role becomes a player first, so the unique role never clashes. */
    private void handOver(List<CampusTeamMember> roster, CampusTeamMember.Role role) {
        if (role == CampusTeamMember.Role.CAPTAIN || role == CampusTeamMember.Role.VICE_CAPTAIN) {
            roster.stream().filter(member -> member.getRole() == role).forEach(member -> member.changeRole(CampusTeamMember.Role.PLAYER));
            players.flush();
        }
    }

    private Long teamOf(Long playerId) {
        return (playerId == null ? Optional.<Long>empty() : players.findTeamIdOf(playerId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PLAYER_NOT_FOUND));
    }

    private CampusTeam openTeam(Long accountId, boolean office, Long teamId) {
        CampusTeam team = managedTeam(accountId, office, teamId);
        if (!team.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, INACTIVE);
        }
        return team;
    }

    /** Checks the manager from the team's stored group before taking the team row lock. */
    private CampusTeam managedTeam(Long accountId, boolean office, Long teamId) {
        Long groupId = (teamId == null ? Optional.<CampusTeam>empty() : teams.findById(teamId)).map(team -> team.getGroup().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, TEAM_NOT_FOUND));
        if (!(office || officers.isOfficer(groupId, accountId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, TEAM_NOT_FOUND);
        }
        return teams.findByIdForUpdate(teamId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, TEAM_NOT_FOUND));
    }

    private CampusGroup managedGroup(Long accountId, boolean office, Long groupId) {
        if (groupId == null || !(office || officers.isOfficer(groupId, accountId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, GROUP_NOT_FOUND);
        }
        return groups.findById(groupId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, GROUP_NOT_FOUND));
    }

    private boolean isActiveMember(Long accountId, Long groupId) {
        return groupId != null && memberships.findCurrentForAccount(accountId).stream()
                .anyMatch(membership -> membership.getGroup().getId().equals(groupId)
                        && membership.getStatus() == CampusGroupMembership.Status.ACTIVE);
    }

    private static TeamView view(CampusTeam team, List<CampusTeamMember> roster) {
        return new TeamView(team.getId(), team.getName(), team.getSeason(), team.isActive(), roster.stream().sorted(ROSTER)
                .map(member -> new PlayerView(member.getId(), member.getAccount().getLoginId(), member.getRole(), member.getJoinedAt())).toList());
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
