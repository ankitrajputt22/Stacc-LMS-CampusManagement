package com.stacc.backend.campus;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.approval.ApprovalRequest;
import com.stacc.backend.approval.ApprovalWorkflowDefinition;
import com.stacc.backend.approval.ApprovalWorkflowDefinition.StepSpec;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.campus.notices.CampusNotice;
import com.stacc.backend.campus.services.CampusServiceDefinition;
import com.stacc.backend.campus.services.CampusServiceRequest;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** Made-up accounts, services, requests, approvals, and notices for the campus tests. */
public final class CampusFixtures {

    public static final Instant NOW = Instant.parse("2026-10-26T09:30:00.123456789Z");

    private CampusFixtures() {
    }

    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static UserAccount account(long id, String loginId, RoleName... roles) {
        UserAccount account = withId(new UserAccount(loginId, "not-a-real-hash"), id);
        for (RoleName role : roles) {
            account.assignRole(new Role(role));
        }
        return account;
    }

    public static CampusServiceDefinition.Settings settings(boolean student, boolean faculty, String workflowCode) {
        return new CampusServiceDefinition.Settings("Service", "Plain text.", true, student, faculty,
                workflowCode != null, workflowCode);
    }

    /** A service; it needs approval with the given workflow, or none when the code is null. */
    public static CampusServiceDefinition service(long id, String code, boolean student, boolean faculty,
            String workflowCode) {
        return withId(new CampusServiceDefinition(code, settings(student, faculty, workflowCode)), id);
    }

    /** A pending approval request of a one-step ADMIN workflow, as the CAMPUS_SERVICE_REQUEST workflow is set up. */
    public static ApprovalRequest approval(long id, UserAccount requester) {
        ApprovalWorkflowDefinition workflow = withId(new ApprovalWorkflowDefinition("CAMPUS_SERVICE_REQUEST",
                "Campus Service Request Approval", null,
                List.of(new StepSpec("Administrative Approval", new Role(RoleName.ADMIN)))), 9);
        ApprovalRequest request = withId(new ApprovalRequest(workflow, requester, "CAMPUS_SERVICE_REQUEST", "1",
                "Campus service request", NOW), id);
        withId(request.getSteps().get(0), id * 10 + 1);
        return request;
    }

    public static ApprovalRequest approved(long id, UserAccount requester, UserAccount admin) {
        ApprovalRequest request = approval(id, requester);
        request.approve(request.currentStep().orElseThrow(), admin, null, NOW);
        return request;
    }

    public static ApprovalRequest rejected(long id, UserAccount requester, UserAccount admin, String comment) {
        ApprovalRequest request = approval(id, requester);
        request.reject(request.currentStep().orElseThrow(), admin, comment, NOW);
        return request;
    }

    public static CampusServiceRequest request(long id, CampusServiceDefinition service, UserAccount requester,
            ApprovalRequest approval) {
        CampusServiceRequest request = withId(new CampusServiceRequest(service, requester, "Subject " + id,
                "Details " + id, NOW), id);
        request.assignNumber();
        if (approval != null) {
            request.linkApproval(approval);
        }
        return request;
    }

    public static CampusNotice notice(long id, boolean student, boolean faculty, boolean admin) {
        return withId(new CampusNotice(new CampusNotice.Content("Notice " + id, "Message " + id, student, faculty,
                admin)), id);
    }

    public static ApprovalActor approvalActor(long accountId, String... authorities) {
        return ApprovalActor.of(accountId, AuthorityUtils.createAuthorityList(authorities));
    }

    public static CampusActor actor(long accountId, String... authorities) {
        return CampusActor.of(accountId, AuthorityUtils.createAuthorityList(authorities));
    }
}
