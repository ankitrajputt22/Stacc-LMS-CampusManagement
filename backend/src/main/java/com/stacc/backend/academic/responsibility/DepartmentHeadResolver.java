package com.stacc.backend.academic.responsibility;

import java.util.Optional;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DepartmentHeadResolver {

    private final DepartmentHeadRepository heads;
    private final StudentProfileRepository students;

    public DepartmentHeadResolver(DepartmentHeadRepository heads, StudentProfileRepository students) {
        this.heads = heads;
        this.students = students;
    }

    public Optional<UserAccount> headOf(Long departmentId) {
        return heads.findActiveByDepartmentId(departmentId).filter(DepartmentHeadResolver::counts)
                .map(DepartmentHead::getUserAccount);
    }

    public Optional<UserAccount> headForStudent(Long studentAccountId) {
        return students.findWithContextByUserAccountId(studentAccountId)
                .flatMap(profile -> headOf(profile.getProgram().getDepartment().getId()));
    }

    public static boolean counts(DepartmentHead head) {
        return head.isActive() && notCountedReason(head.getUserAccount()) == null;
    }

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
