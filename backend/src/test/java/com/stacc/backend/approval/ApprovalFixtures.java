package com.stacc.backend.approval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.stacc.backend.approval.ApprovalWorkflowDefinition.StepSpec;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.permission.Permission;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** Made-up roles, accounts, workflows, and requests for the approval tests, with IDs set the way the database would. */
final class ApprovalFixtures {

    static final Instant NOW = Instant.parse("2026-10-26T09:30:00.123456789Z");

    private ApprovalFixtures() {
    }

    static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    /** A role as V49 sets it up: FACULTY and ADMIN can decide approval steps, STUDENT cannot. */
    static Role role(RoleName name) {
        Role role = withId(new Role(name), name.ordinal() + 1);
        if (name != RoleName.STUDENT) {
            role.assignPermission(new Permission(ApprovalActor.TASK_DECIDE));
        }
        return role;
    }

    static UserAccount account(long id, String loginId, RoleName... roles) {
        UserAccount account = withId(new UserAccount(loginId, "not-a-real-hash"), id);
        for (RoleName role : roles) {
            account.assignRole(role(role));
        }
        return account;
    }

    static ApprovalWorkflowDefinition workflow(long id, String code, RoleName... stepRoles) {
        List<StepSpec> steps = new ArrayList<>();
        for (int index = 0; index < stepRoles.length; index++) {
            steps.add(new StepSpec("Step " + (index + 1), role(stepRoles[index])));
        }
        ApprovalWorkflowDefinition workflow = withId(new ApprovalWorkflowDefinition(code, "Workflow " + code, null, steps), id);
        long stepId = id * 100;
        for (ApprovalWorkflowStepDefinition step : workflow.getSteps()) {
            withId(step, ++stepId);
        }
        return workflow;
    }

    /** Gives a new request and its steps IDs, as saving it would. Step IDs are the request ID times 10 plus the order. */
    static ApprovalRequest saved(ApprovalRequest request, AtomicLong ids) {
        if (request.getId() == null) {
            withId(request, ids.incrementAndGet());
        }
        for (ApprovalRequestStep step : request.getSteps()) {
            if (step.getId() == null) {
                withId(step, request.getId() * 10 + step.getStepOrder());
            }
        }
        return request;
    }

    static ApprovalRequest request(long id, ApprovalWorkflowDefinition workflow, UserAccount requester) {
        ApprovalRequest request = new ApprovalRequest(workflow, requester, "DEMO_SOURCE", "source-" + id,
                "Demo approval " + id, NOW);
        return saved(withId(request, id), new AtomicLong());
    }

    static ApprovalActor actor(long accountId, String... authorities) {
        return ApprovalActor.of(accountId, AuthorityUtils.createAuthorityList(authorities));
    }

    /** An approver with the task permissions, as V49 gives FACULTY and ADMIN, holding these roles. */
    static ApprovalActor approver(long accountId, RoleName... roles) {
        List<String> authorities = new ArrayList<>(List.of(ApprovalActor.REQUEST_VIEW_OWN, ApprovalActor.TASK_VIEW,
                ApprovalActor.TASK_DECIDE));
        for (RoleName role : roles) {
            authorities.add("ROLE_" + role.name());
        }
        return actor(accountId, authorities.toArray(String[]::new));
    }
}
