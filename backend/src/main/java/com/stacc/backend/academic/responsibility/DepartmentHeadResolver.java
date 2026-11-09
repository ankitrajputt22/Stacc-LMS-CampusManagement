package com.stacc.backend.academic.responsibility;

import java.util.Optional;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out who heads a department now, every time it is asked: the department's active term counts only while its
 * account is active and still holds {@code HOD}. Nothing is stored or cached, so disabling the account or removing the
 * role takes effect at once. It only reads. Later features (approvals that go to "the student's HOD") ask here instead
 * of repeating the rule.
 */
@Service
@Transactional(readOnly = true)
public class DepartmentHeadResolver {

    private final DepartmentHeadRepository heads;
    private final StudentProfileRepository students;

    public DepartmentHeadResolver(DepartmentHeadRepository heads, StudentProfileRepository students) {
        this.heads = heads;
        this.students = students;
    }

    /** The account that heads the department now, or empty when there is none or its term does not count. */
    public Optional<UserAccount> headOf(Long departmentId) {
        return heads.findActiveByDepartmentId(departmentId).filter(DepartmentHeadResolver::counts)
                .map(DepartmentHead::getUserAccount);
    }

    /** The head of the department that owns the student's program, or empty when there is no student profile or head. */
    public Optional<UserAccount> headForStudent(Long studentAccountId) {
        return students.findWithContextByUserAccountId(studentAccountId)
                .flatMap(profile -> headOf(profile.getProgram().getDepartment().getId()));
    }

    /** Whether an active term counts: its account is active and holds the HOD role. */
    public static boolean counts(DepartmentHead head) {
        return head.isActive() && notCountedReason(head.getUserAccount()) == null;
    }

    /** Why this account's term does not count now, or null when it does. */
    public static String notCountedReason(UserAccount account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return "The account is disabled.";
        }
        if (!account.hasRole(RoleName.HOD)) {
            return "The account no longer holds the Head of Department role.";
        }
        return null;
    }
}
