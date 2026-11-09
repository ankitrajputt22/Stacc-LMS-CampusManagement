package com.stacc.backend.academic.responsibility;

import java.util.Optional;

import com.stacc.backend.approval.ApproverRelationship;
import com.stacc.backend.approval.ApproverRelationshipResolver;
import com.stacc.backend.auth.account.UserAccount;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The head of the requesting student's department, by the one rule in {@link DepartmentHeadResolver}. */
@Component
public class StudentDepartmentHeadApprover implements ApproverRelationshipResolver {

    private final DepartmentHeadResolver heads;

    public StudentDepartmentHeadApprover(DepartmentHeadResolver heads) {
        this.heads = heads;
    }

    @Override
    public ApproverRelationship relationship() {
        return ApproverRelationship.STUDENT_DEPARTMENT_HEAD;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> approverFor(Long requesterAccountId) {
        return heads.headForStudent(requesterAccountId).map(UserAccount::getId);
    }
}
