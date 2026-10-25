package com.stacc.backend.approval.api;

import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.auth.security.AuthenticatedAccount;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The route rules of the approval API, in one place. They let a caller ask; ownership and the role each step requires
 * are still checked per request and per step. There is no admin pass.
 */
final class ApprovalAccess {

    private static final String ANY_ROLE = "hasAnyRole('STUDENT', 'FACULTY', 'ADMIN')";

    static final String VIEW_OWN = ANY_ROLE + " and hasAuthority('APPROVAL_REQUEST_VIEW_OWN')";
    static final String TASK_VIEW = ANY_ROLE + " and hasAuthority('APPROVAL_TASK_VIEW')";
    static final String TASK_DECIDE = ANY_ROLE + " and hasAuthority('APPROVAL_TASK_DECIDE')";
    static final String WORKFLOW_MANAGE = "hasRole('ADMIN') and hasAuthority('APPROVAL_WORKFLOW_MANAGE')";

    private ApprovalAccess() {
    }

    static ApprovalActor actor(Jwt accessToken, Authentication authentication) {
        return ApprovalActor.of(AuthenticatedAccount.idFrom(accessToken), authentication.getAuthorities());
    }
}
