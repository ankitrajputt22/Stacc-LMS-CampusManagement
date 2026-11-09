package com.stacc.backend.academic.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.admin.DepartmentHeadRecords.AssignDepartmentHead;
import com.stacc.backend.academic.admin.DepartmentHeadRecords.DepartmentHeadRow;
import com.stacc.backend.academic.admin.DepartmentHeadRecords.HeadCandidate;
import com.stacc.backend.academic.admin.DepartmentHeadRecords.HeadTerm;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.department.DepartmentStatus;
import com.stacc.backend.academic.responsibility.DepartmentHead;
import com.stacc.backend.academic.responsibility.DepartmentHeadRepository;
import com.stacc.backend.academic.responsibility.DepartmentHeadResolver;
import com.stacc.backend.audit.AuditAction;
import com.stacc.backend.audit.AuditService;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.identity.person.PersonProfile;
import com.stacc.backend.identity.person.PersonProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins decide who heads each department. Assigning a new head ends the current term and starts a new one, so the
 * history stays; ending a term keeps it. Every change locks the department first, so a department never has two
 * active heads, and is recorded in the audit log in the same transaction. Nothing is deleted.
 */
@Service
@Transactional
public class DepartmentHeadService {

    static final String TARGET_TYPE = "DEPARTMENT";
    static final String DEPARTMENT_MISSING = "The selected department does not exist.";
    static final String DEPARTMENT_INACTIVE = "This department is inactive, so it cannot get a new head.";
    static final String ACCOUNT_MISSING = "The selected account does not exist.";
    static final String ACCOUNT_INACTIVE = "This account is disabled, so it cannot head a department.";
    static final String NOT_HOD = "This account does not hold the Head of Department role. Give it that role on its "
            + "account page first.";

    private final DepartmentRepository departments;
    private final DepartmentHeadRepository heads;
    private final UserAccountRepository accounts;
    private final PersonProfileRepository people;
    private final AuditService audit;
    private final Clock clock;

    public DepartmentHeadService(DepartmentRepository departments, DepartmentHeadRepository heads,
            UserAccountRepository accounts, PersonProfileRepository people, AuditService audit, Clock clock) {
        this.departments = departments;
        this.heads = heads;
        this.accounts = accounts;
        this.people = people;
        this.audit = audit;
        this.clock = clock;
    }

    /** Every department by code, with its active term or none. */
    @Transactional(readOnly = true)
    public List<DepartmentHeadRow> overview() {
        Map<Long, DepartmentHead> active = heads.findAllActive().stream()
                .collect(Collectors.toMap(head -> head.getDepartment().getId(), Function.identity()));
        Map<Long, String> names = namesOf(active.values().stream().map(head -> head.getUserAccount().getId()).toList());
        return departments.findAllByOrderByCodeAscIdAsc().stream()
                .map(department -> row(department, active.get(department.getId()), names)).toList();
    }

    /** Every term of one department, the latest first. */
    @Transactional(readOnly = true)
    public List<HeadTerm> history(Long departmentId) {
        if (!departments.existsById(departmentId)) {
            throw AcademicMasterErrors.notFound(DEPARTMENT_MISSING);
        }
        List<DepartmentHead> terms = heads.findAllByDepartmentId(departmentId);
        Map<Long, String> names = namesOf(terms.stream().map(head -> head.getUserAccount().getId()).toList());
        return terms.stream().map(head -> term(head, names)).toList();
    }

    /** The accounts that can be made a head: active and holding HOD, by login ID. */
    @Transactional(readOnly = true)
    public List<HeadCandidate> candidates() {
        List<UserAccount> found = accounts.findAllByRoleNameAndStatus(RoleName.HOD, AccountStatus.ACTIVE);
        Map<Long, String> names = namesOf(found.stream().map(UserAccount::getId).toList());
        return found.stream().map(account -> new HeadCandidate(account.getId(), account.getLoginId(),
                names.get(account.getId()))).toList();
    }

    /**
     * Makes the account the department's head. The current head's term ends first; the same head again changes
     * nothing. An account may head more than one department.
     */
    public DepartmentHeadRow assign(Long actorAccountId, Long departmentId, AssignDepartmentHead request) {
        Department department = lock(departmentId);
        if (department.getStatus() != DepartmentStatus.ACTIVE) {
            throw AcademicMasterErrors.conflict(DEPARTMENT_INACTIVE);
        }
        UserAccount account = accounts.findById(request.userAccountId())
                .orElseThrow(() -> AcademicMasterErrors.notFound(ACCOUNT_MISSING));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw AcademicMasterErrors.conflict(ACCOUNT_INACTIVE);
        }
        if (!account.hasRole(RoleName.HOD)) {
            throw AcademicMasterErrors.conflict(NOT_HOD);
        }
        DepartmentHead current = heads.findActiveByDepartmentId(departmentId).orElse(null);
        if (current != null && current.getUserAccount().getId().equals(account.getId())) {
            return describe(department, current);
        }
        Instant now = clock.instant();
        if (current != null) {
            endTerm(actorAccountId, department, current, now);
        }
        DepartmentHead started = heads.saveAndFlush(new DepartmentHead(department, account, now));
        audit.record(actorAccountId, AuditAction.DEPARTMENT_HEAD_ASSIGNED, TARGET_TYPE, department.getId(),
                account.getLoginId() + " now heads " + department.getCode() + ".");
        return describe(department, started);
    }

    /** Ends the department's current term, if it has one. Without one, nothing changes. */
    public DepartmentHeadRow end(Long actorAccountId, Long departmentId) {
        Department department = lock(departmentId);
        heads.findActiveByDepartmentId(departmentId)
                .ifPresent(current -> endTerm(actorAccountId, department, current, clock.instant()));
        return describe(department, null);
    }

    private void endTerm(Long actorAccountId, Department department, DepartmentHead current, Instant now) {
        current.end(now);
        heads.saveAndFlush(current);
        audit.record(actorAccountId, AuditAction.DEPARTMENT_HEAD_ENDED, TARGET_TYPE, department.getId(),
                current.getUserAccount().getLoginId() + " no longer heads " + department.getCode() + ".");
    }

    private Department lock(Long departmentId) {
        return departments.findByIdForUpdate(departmentId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(DEPARTMENT_MISSING));
    }

    private DepartmentHeadRow describe(Department department, DepartmentHead head) {
        return row(department, head, head == null ? Map.of() : namesOf(List.of(head.getUserAccount().getId())));
    }

    private static DepartmentHeadRow row(Department department, DepartmentHead head, Map<Long, String> names) {
        return new DepartmentHeadRow(department.getId(), department.getCode(), department.getName(),
                department.getStatus().name(), head == null ? null : term(head, names));
    }

    private static HeadTerm term(DepartmentHead head, Map<Long, String> names) {
        UserAccount account = head.getUserAccount();
        String reason = head.isActive() ? DepartmentHeadResolver.notCountedReason(account) : null;
        return new HeadTerm(head.getId(), account.getId(), account.getLoginId(), names.get(account.getId()),
                head.getStatus().name(), head.getStartedAt(), head.getEndedAt(), head.isActive() && reason == null, reason);
    }

    private Map<Long, String> namesOf(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return people.findAllByUserAccountIdIn(accountIds).stream().collect(Collectors.toMap(
                person -> person.getUserAccount().getId(), PersonProfile::displayName, (first, second) -> first));
    }
}
