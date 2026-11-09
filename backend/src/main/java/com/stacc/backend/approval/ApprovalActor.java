package com.stacc.backend.approval;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Who is acting on approvals: the account in the access token, the role codes it holds (all of them, in no particular
 * order; none is the main one), and its approval permissions. It is read from the token only.
 */
public record ApprovalActor(Long accountId, Set<String> roleCodes, boolean viewOwn, boolean taskView,
        boolean taskDecide, boolean manageWorkflows) {

    public static final String REQUEST_VIEW_OWN = "APPROVAL_REQUEST_VIEW_OWN";
    public static final String TASK_VIEW = "APPROVAL_TASK_VIEW";
    public static final String TASK_DECIDE = "APPROVAL_TASK_DECIDE";
    public static final String WORKFLOW_MANAGE = "APPROVAL_WORKFLOW_MANAGE";
    private static final String ROLE_PREFIX = "ROLE_";

    public ApprovalActor {
        roleCodes = Set.copyOf(roleCodes);
    }

    public static ApprovalActor of(Long accountId, Collection<? extends GrantedAuthority> authorities) {
        Set<String> names = AuthorityUtils.authorityListToSet(authorities);
        Set<String> roles = names.stream()
                .filter(name -> name.startsWith(ROLE_PREFIX))
                .map(name -> name.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toSet());
        return new ApprovalActor(accountId, roles, names.contains(REQUEST_VIEW_OWN), names.contains(TASK_VIEW),
                names.contains(TASK_DECIDE), names.contains(WORKFLOW_MANAGE));
    }
}
