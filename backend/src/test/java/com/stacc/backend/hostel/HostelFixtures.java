package com.stacc.backend.hostel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.approval.ApprovalRequest;
import com.stacc.backend.approval.ApprovalWorkflowDefinition;
import com.stacc.backend.approval.ApprovalWorkflowDefinition.StepSpec;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.identity.student.StudentProfile;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** Made-up accounts, students, inventory, applications, and approvals for the hostel tests. */
final class HostelFixtures {

    static final Instant NOW = Instant.parse("2026-10-26T09:30:00.123456789Z");

    private HostelFixtures() {
    }

    static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    static UserAccount account(long id, String loginId, RoleName... roles) {
        UserAccount account = withId(new UserAccount(loginId, "not-a-real-hash"), id);
        for (RoleName role : roles) {
            account.assignRole(new Role(role));
        }
        return account;
    }

    static AcademicSession session(long id, String code, int startYear) {
        return withId(new AcademicSession(code, LocalDate.of(startYear, 7, 1), LocalDate.of(startYear + 1, 6, 30)), id);
    }

    static StudentProfile student(long id, UserAccount account) {
        Department cse = withId(new Department("CSE", "Computer Science"), 3);
        Program program = withId(new Program(cse, "BTECH-CSE", "B.Tech CSE", 8), 4);
        return withId(new StudentProfile(account, program, session(1, "2025-26", 2025)), id);
    }

    static Hostel hostel(long id, String code) {
        return withId(new Hostel(code, "Hostel " + code, null), id);
    }

    static HostelRoom room(long id, Hostel hostel, String number) {
        return withId(new HostelRoom(hostel, number), id);
    }

    static HostelBed bed(long id, HostelRoom room, String number) {
        return withId(new HostelBed(room, number), id);
    }

    /** A pending approval request of a one-step ADMIN workflow, as the HOSTEL_APPLICATION workflow is set up. */
    static ApprovalRequest approval(long id, UserAccount requester) {
        ApprovalWorkflowDefinition workflow = withId(new ApprovalWorkflowDefinition("HOSTEL_APPLICATION",
                "Hostel Application Approval", null, List.of(new StepSpec("Administrative Approval", new Role(RoleName.ADMIN)))), 9);
        ApprovalRequest request = withId(new ApprovalRequest(workflow, requester, "HOSTEL_APPLICATION", "1",
                "Hostel application", NOW), id);
        withId(request.getSteps().get(0), id * 10 + 1);
        return request;
    }

    static ApprovalRequest approved(long id, UserAccount requester, UserAccount admin) {
        ApprovalRequest request = approval(id, requester);
        request.approve(request.currentStep().orElseThrow(), admin, null, NOW);
        return request;
    }

    static HostelApplication application(long id, StudentProfile student, AcademicSession session, ApprovalRequest approval) {
        HostelApplication application = withId(new HostelApplication(student, session, null, NOW), id);
        application.assignNumber();
        if (approval != null) {
            application.linkApproval(approval);
        }
        return application;
    }

    static ApprovalActor actor(long accountId, String... authorities) {
        return ApprovalActor.of(accountId, AuthorityUtils.createAuthorityList(authorities));
    }
}
