package com.stacc.backend.engagement.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.engagement.groups.CampusGroup;
import org.junit.jupiter.api.Test;

/** Teams belong to active sports groups and need a name and season; a player who left changes no more. */
class TeamRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    private static CampusGroup group(CampusGroup.Kind kind, boolean active) {
        CampusGroup group = mock(CampusGroup.class);
        when(group.getKind()).thenReturn(kind);
        when(group.isActive()).thenReturn(active);
        return group;
    }

    @Test
    void teamsBelongToActiveSportsGroupsAndNeedANameAndSeason() {
        assertThatThrownBy(() -> new CampusTeam(group(CampusGroup.Kind.CLUB, true), "First XI", "2026-27", 1L, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CampusTeam(group(CampusGroup.Kind.SPORT, false), "First XI", "2026-27", 1L, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CampusTeam(group(CampusGroup.Kind.SPORT, true), " ", "2026-27", 1L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        CampusTeam team = new CampusTeam(group(CampusGroup.Kind.SPORT, true), " First XI ", "2026-27", 1L, NOW);
        assertThat(team.getName()).isEqualTo("First XI");
        assertThat(team.isActive()).isTrue();
    }

    @Test
    void aPlayerWhoLeftChangesNoMore() {
        CampusTeamMember player = new CampusTeamMember(mock(CampusTeam.class), mock(UserAccount.class), null, NOW);
        assertThat(player.getRole()).isEqualTo(CampusTeamMember.Role.PLAYER);
        player.changeRole(CampusTeamMember.Role.CAPTAIN);
        player.leave(NOW);
        assertThatThrownBy(() -> player.changeRole(CampusTeamMember.Role.PLAYER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> player.leave(NOW)).isInstanceOf(IllegalStateException.class);
    }
}
