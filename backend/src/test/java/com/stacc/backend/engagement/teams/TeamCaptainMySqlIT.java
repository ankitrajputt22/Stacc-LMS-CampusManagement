package com.stacc.backend.engagement.teams;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.engagement.groups.CampusGroup;
import com.stacc.backend.engagement.groups.CampusGroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Naming two captains at once leaves exactly one: the team row lock orders the hand-overs. A group of its own. */
class TeamCaptainMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private TeamService teams;

    @Autowired
    private CampusGroupService groups;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void aTeamNeverHasTwoCaptains() throws Exception {
        Long admin = accounts.findByLoginId("admin1").orElseThrow().getId();
        Long first = accounts.findByLoginId("student1").orElseThrow().getId();
        Long second = accounts.findByLoginId("student2").orElseThrow().getId();
        Long groupId = groups.create("RACE_TEAM_SPORT", "Race Team Sport", CampusGroup.Kind.SPORT, null, CampusGroup.JoinPolicy.OPEN).groupId();
        groups.join(first, groupId);
        groups.join(second, groupId);
        Long teamId = teams.create(admin, true, groupId, "First team", "2026-27").teamId();
        teams.addPlayer(admin, true, teamId, "student1", CampusTeamMember.Role.PLAYER);
        List<TeamService.PlayerView> roster = teams.addPlayer(admin, true, teamId, "student2", CampusTeamMember.Role.PLAYER).players();

        List<Concurrently.Outcome<TeamService.TeamView>> outcomes = Concurrently.run(
                () -> teams.changeRole(admin, true, roster.get(0).playerId(), CampusTeamMember.Role.CAPTAIN),
                () -> teams.changeRole(admin, true, roster.get(1).playerId(), CampusTeamMember.Role.CAPTAIN));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        List<TeamService.PlayerView> after = teams.forGroup(admin, true, groupId).teams().getFirst().players();
        assertThat(after).filteredOn(player -> player.role() == CampusTeamMember.Role.CAPTAIN).hasSize(1);
        assertThat(after).hasSize(2);
    }
}
