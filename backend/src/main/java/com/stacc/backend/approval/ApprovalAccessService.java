package com.stacc.backend.approval;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The one place that says who may see an approval request and who may decide a step. A requester sees only their own
 * requests. A step is decided only by an account holding the exact role the step requires, with APPROVAL_TASK_DECIDE,
 * and never by the requester. There is no admin pass: ADMIN decides only steps that require ADMIN.
 */
@Component
public class ApprovalAccessService {

    static final String CANNOT_DECIDE = "You cannot decide approval steps.";
    static final String OWN_REQUEST = "You cannot decide your own approval request.";

    /** The requester, with APPROVAL_REQUEST_VIEW_OWN. */
    public boolean canView(ApprovalActor actor, ApprovalRequest request) {
        return actor.viewOwn() && actor.accountId() != null && actor.accountId().equals(request.getRequester().getId());
    }

    /** Whether the account in the token holds this role code. The order of the roles never matters. */
    public boolean holdsRole(ApprovalActor actor, String roleCode) {
        return actor.roleCodes().contains(roleCode);
    }

    /**
     * Refuses with 403 unless the actor may decide this step: APPROVAL_TASK_DECIDE, not the requester, the step's role in
     * the token, and the same role still held by the active account in the database, so a role removed after sign-in
     * decides nothing.
     */
    public void requireDecider(ApprovalActor actor, UserAccount account, ApprovalRequest request, ApprovalRequestStep step) {
        if (!actor.taskDecide()) {
            throw new ApiException(HttpStatus.FORBIDDEN, CANNOT_DECIDE);
        }
        if (account.getId().equals(request.getRequester().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, OWN_REQUEST);
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
