package com.stacc.backend.approval;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApprovalAccessService {

    static final String CANNOT_DECIDE = "You cannot decide approval steps.";
    static final String OWN_REQUEST = "You cannot decide your own approval request.";

    public boolean canView(ApprovalActor actor, ApprovalRequest request) {
        return actor.viewOwn() && actor.accountId() != null && actor.accountId().equals(request.getRequester().getId());
    }

    public boolean holdsRole(ApprovalActor actor, String roleCode) {
        return actor.roleCodes().contains(roleCode);
    }

    public void requireDecider(ApprovalActor actor, UserAccount account, ApprovalRequest request, ApprovalRequestStep step) {
        if (!actor.taskDecide()) {
            throw new ApiException(HttpStatus.FORBIDDEN, CANNOT_DECIDE);
        }
        if (account.getId().equals(request.getRequester().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, OWN_REQUEST);
        }
        if (step.getAssignedApprover() != null && !step.getAssignedApprover().getId().equals(account.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This step must be decided by the approver assigned to it.");
        }
        String role = step.getRequiredRoleCode();
        if (!holdsRole(actor, role) || account.getStatus() != AccountStatus.ACTIVE || !accountHolds(account, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "This step must be decided by an account holding the " + role + " role.");
        }
    }

    private static boolean accountHolds(UserAccount account, String roleCode) {
        try {
            return account.hasRole(RoleName.valueOf(roleCode));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }
}
