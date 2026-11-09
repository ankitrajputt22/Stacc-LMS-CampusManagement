package com.stacc.backend.engagement.groups;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.stacc.backend.approval.ApproverRelationship;
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
 * Clubs and sports groups: admins create and edit groups and name officers (an ADVISOR must hold FACULTY); people join by
 * the group's policy and leave; officers accept or refuse requests to join. The person always comes from the token.
 */
@Service
@Transactional
public class CampusGroupService {

    public record OfficerView(Long officerId, String loginId, CampusGroupOfficer.Title title) {
    }

    public record GroupView(Long groupId, String code, String name, CampusGroup.Kind kind, String description,
            CampusGroup.JoinPolicy joinPolicy, boolean active, long members, List<OfficerView> officers,
            String myMembership, boolean iAmOfficer) {
    }

    public record MemberView(Long membershipId, String loginId, CampusGroupMembership.Status status, Instant requestedAt) {
    }

    private final CampusGroupRepository groups;
    private final CampusGroupOfficerRepository officers;
    private final CampusGroupMembershipRepository memberships;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public CampusGroupService(CampusGroupRepository groups, CampusGroupOfficerRepository officers,
            CampusGroupMembershipRepository memberships, UserAccountRepository accounts, Clock clock) {
        this.groups = groups;
        this.officers = officers;
        this.memberships = memberships;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GroupView> list(Long accountId, boolean includeInactive) {
        Map<Long, Long> counts = memberships.countActive().stream()
                .collect(Collectors.toMap(CampusGroupMembershipRepository.GroupCount::getGroupId, CampusGroupMembershipRepository.GroupCount::getMembers));
        Map<Long, List<CampusGroupOfficer>> officersByGroup = officers.findAllActive().stream()
                .collect(Collectors.groupingBy(o -> o.getGroup().getId()));
        Map<Long, CampusGroupMembership> mine = accountId == null ? Map.of() : memberships.findCurrentForAccount(accountId).stream()
                .collect(Collectors.toMap(m -> m.getGroup().getId(), m -> m, (a, b) -> a));
        return groups.findAllByOrderByNameAscIdAsc().stream()
                .filter(group -> includeInactive || group.isActive())
                .map(group -> {
                    List<CampusGroupOfficer> groupOfficers = officersByGroup.getOrDefault(group.getId(), List.of());
                    return new GroupView(group.getId(), group.getCode(), group.getName(), group.getKind(), group.getDescription(),
                            group.getJoinPolicy(), group.isActive(), counts.getOrDefault(group.getId(), 0L),
                            groupOfficers.stream().map(o -> new OfficerView(o.getId(), o.getAccount().getLoginId(), o.getTitle())).toList(),
                            Optional.ofNullable(mine.get(group.getId())).map(m -> m.getStatus().name()).orElse(null),
                            accountId != null && groupOfficers.stream().anyMatch(o -> o.getAccount().getId().equals(accountId)));
                })
                .toList();
    }

    // --- Admin

    public GroupView create(String code, String name, CampusGroup.Kind kind, String description, CampusGroup.JoinPolicy policy) {
        CampusGroup group;
        try {
            group = new CampusGroup(code, name, kind, description, policy);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The group is not valid: " + invalid.getMessage() + ".");
        }
        if (groups.existsByCode(group.getCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "A group with this code already exists.");
        }
        try {
            groups.saveAndFlush(group);
        } catch (DataIntegrityViolationException race) {
            throw new ApiException(HttpStatus.CONFLICT, "A group with this code already exists.");
        }
        return view(group.getId(), null);
    }

    public GroupView update(Long groupId, String name, CampusGroup.Kind kind, String description, CampusGroup.JoinPolicy policy, boolean active) {
        CampusGroup group = groups.findByIdForUpdate(groupId == null ? -1L : groupId).orElseThrow(CampusGroupService::missing);
        try {
            group.update(name, kind, description, policy, active);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The group is not valid: " + invalid.getMessage() + ".");
        }
        return view(groupId, null);
    }

    public GroupView addOfficer(Long groupId, String loginId, CampusGroupOfficer.Title title) {
        CampusGroup group = groups.findByIdForUpdate(groupId == null ? -1L : groupId).orElseThrow(CampusGroupService::missing);
        UserAccount account = accounts.findByLoginId(loginId == null ? "" : loginId.strip())
                .filter(found -> found.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No active account has this login ID."));
        boolean faculty = account.hasRole(RoleName.FACULTY);
        if ((title == CampusGroupOfficer.Title.ADVISOR || title == CampusGroupOfficer.Title.COACH) && !faculty) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "An advisor or coach must be a faculty member.");
        }
        if (!faculty && !account.hasRole(RoleName.STUDENT)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Officers are students or faculty members.");
        }
        officers.saveAndFlush(new CampusGroupOfficer(group, account, title, now()));
        return view(groupId, null);
    }

    public GroupView endOfficer(Long officerId) {
        CampusGroupOfficer officer = officers.findOne(officerId == null ? -1L : officerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Officer not found."));
        officer.end(now());
        return view(officer.getGroup().getId(), null);
    }

    // --- Members

    /** Joins at once for an OPEN group, asks for an APPROVAL group, and is refused for a CLOSED or inactive one. */
    public GroupView join(Long accountId, Long groupId) {
        CampusGroup group = groups.findByIdForUpdate(groupId == null ? -1L : groupId).filter(CampusGroup::isActive)
                .orElseThrow(CampusGroupService::missing);
        if (group.getJoinPolicy() == CampusGroup.JoinPolicy.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "This group is not taking new members.");
        }
        if (memberships.findCurrentForUpdate(groupId, accountId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "You are already a member or have asked to join.");
        }
        memberships.saveAndFlush(new CampusGroupMembership(group, accounts.getReferenceById(accountId),
                group.getJoinPolicy() == CampusGroup.JoinPolicy.OPEN, now()));
        return view(groupId, accountId);
    }

    public GroupView leave(Long accountId, Long groupId) {
        memberships.findCurrentForUpdate(groupId, accountId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "You are not a member."))
                .leave(now());
        return view(groupId, accountId);
    }

    /** The current members and requests of a group, for its officers and admins. */
    @Transactional(readOnly = true)
    public List<MemberView> members(Long accountId, boolean admin, Long groupId) {
        requireOfficerOrAdmin(accountId, admin, groupId);
        return memberships.findCurrentForGroup(groupId).stream()
                .map(m -> new MemberView(m.getId(), m.getAccount().getLoginId(), m.getStatus(), m.getRequestedAt())).toList();
    }

    public List<MemberView> decide(Long accountId, boolean admin, Long membershipId, boolean accept) {
        CampusGroupMembership membership = memberships.findByIdForUpdate(membershipId == null ? -1L : membershipId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found."));
        Long groupId = membership.getGroup().getId();
        requireOfficerOrAdmin(accountId, admin, groupId);
        try {
            membership.decide(accept, now());
        } catch (IllegalStateException decided) {
            throw new ApiException(HttpStatus.CONFLICT, "This request was already decided.");
        }
        return members(accountId, admin, groupId);
    }

    @Transactional(readOnly = true)
    public boolean isOfficer(Long accountId, Long groupId) {
        return officers.isOfficer(groupId, accountId);
    }

    /** The current advisor of a group, for {@link ApproverRelationship#GROUP_ADVISOR}. */
    @Transactional(readOnly = true)
    public Optional<Long> advisorOf(Long groupId) {
        return officers.findAdvisors(groupId).stream().map(o -> o.getAccount().getId()).findFirst();
    }

    private void requireOfficerOrAdmin(Long accountId, boolean admin, Long groupId) {
        if (!admin && !officers.isOfficer(groupId, accountId)) {
            throw missing();
        }
    }

    private GroupView view(Long groupId, Long accountId) {
        return list(accountId, true).stream().filter(group -> group.groupId().equals(groupId)).findFirst().orElseThrow(CampusGroupService::missing);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "Group not found.");
    }
}
