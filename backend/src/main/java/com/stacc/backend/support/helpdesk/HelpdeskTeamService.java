package com.stacc.backend.support.helpdesk;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Support teams, their members, and which team each category routes to. Admins with HELPDESK_TICKET_MANAGE only.
 * Members must be active accounts holding ADMIN or a role with HELPDESK_TEAM_WORK. Teams are made inactive, never
 * deleted; replacing the member list changes only who works future and open tickets.
 */
@Service
@Transactional
public class HelpdeskTeamService {

    public record TeamView(Long teamId, String code, String name, boolean active, List<String> memberLoginIds) {
    }

    private final HelpdeskTeamRepository teams;
    private final HelpdeskTeamMemberRepository members;
    private final HelpdeskCategoryRepository categories;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public HelpdeskTeamService(HelpdeskTeamRepository teams, HelpdeskTeamMemberRepository members,
            HelpdeskCategoryRepository categories, UserAccountRepository accounts, Clock clock) {
        this.teams = teams;
        this.members = members;
        this.categories = categories;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TeamView> all() {
        Map<Long, List<String>> byTeam = members.findAllWithAccountAndTeam().stream().collect(Collectors.groupingBy(
                member -> member.getTeam().getId(), Collectors.mapping(member -> member.getAccount().getLoginId(), Collectors.toList())));
        return teams.findAllByOrderByNameAscIdAsc().stream()
                .map(team -> new TeamView(team.getId(), team.getCode(), team.getName(), team.isActive(),
                        byTeam.getOrDefault(team.getId(), List.of())))
                .toList();
    }

    public TeamView create(String code, String name) {
        HelpdeskTeam team;
        try {
            team = new HelpdeskTeam(code, name);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The team is not valid: " + invalid.getMessage() + ".");
        }
        if (teams.findByCode(team.getCode()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "A team with this code already exists.");
        }
        try {
            team = teams.saveAndFlush(team);
        } catch (DataIntegrityViolationException race) {
            throw new ApiException(HttpStatus.CONFLICT, "A team with this code already exists.");
        }
        return view(team);
    }

    public TeamView update(Long teamId, String name, boolean active) {
        HelpdeskTeam team = team(teamId);
        try {
            team.update(name, active);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The team is not valid: " + invalid.getMessage() + ".");
        }
        return view(teams.saveAndFlush(team));
    }

    /** Replaces the member list with these login IDs. */
    public TeamView setMembers(Long teamId, List<String> loginIds) {
        HelpdeskTeam team = team(teamId);
        Set<String> wanted = new LinkedHashSet<>();
        (loginIds == null ? List.<String>of() : loginIds).forEach(id -> wanted.add(id == null ? "" : id.strip()));
        if (wanted.size() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A team has at most 50 members.");
        }
        List<UserAccount> chosen = new ArrayList<>();
        for (String loginId : wanted) {
            UserAccount account = accounts.findWithRolesAndPermissionsByLoginId(loginId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No account has the login ID " + loginId + "."));
            boolean canWork = account.hasRole(RoleName.ADMIN)
                    || account.getRoles().stream().anyMatch(role -> role.hasPermission(HelpdeskActor.TEAM_WORK));
            if (account.getStatus() != AccountStatus.ACTIVE || !canWork) {
                throw new ApiException(HttpStatus.BAD_REQUEST, loginId + " cannot work on support teams.");
            }
            chosen.add(account);
        }
        List<HelpdeskTeamMember> current = members.findAllWithAccountByTeamId(teamId);
        Set<Long> chosenIds = chosen.stream().map(UserAccount::getId).collect(Collectors.toSet());
        Set<Long> currentIds = current.stream().map(member -> member.getAccount().getId()).collect(Collectors.toSet());
        members.deleteAll(current.stream().filter(member -> !chosenIds.contains(member.getAccount().getId())).toList());
        chosen.stream().filter(account -> !currentIds.contains(account.getId()))
                .forEach(account -> members.save(new HelpdeskTeamMember(team, account, clock.instant())));
        members.flush();
        return view(team);
    }

    /** Routes a category's new tickets to an active team, or to none with null. */
    public void routeCategory(Long categoryId, Long teamId) {
        HelpdeskCategory category = categories.findById(categoryId == null ? -1L : categoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
        HelpdeskTeam team = teamId == null ? null : team(teamId);
        if (team != null && !team.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "An inactive team takes no new tickets.");
        }
        category.routeTo(team);
        categories.saveAndFlush(category);
    }

    @Transactional(readOnly = true)
    public HelpdeskTeam team(Long teamId) {
        return teams.findById(teamId == null ? -1L : teamId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Team not found."));
    }

    private TeamView view(HelpdeskTeam team) {
        return new TeamView(team.getId(), team.getCode(), team.getName(), team.isActive(),
                members.findAllWithAccountByTeamId(team.getId()).stream().map(member -> member.getAccount().getLoginId()).toList());
    }
}
