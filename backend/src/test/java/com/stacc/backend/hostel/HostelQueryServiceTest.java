package com.stacc.backend.hostel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.approval.ApprovalCommands.CurrentApprovalStep;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalRequest;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.hostel.HostelResponses.HostelAdminApplicationDetail;
import com.stacc.backend.hostel.HostelResponses.HostelApplicationDetail;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HostelQueryServiceTest {

    private final HostelApplicationRepository applications = mock(HostelApplicationRepository.class);
    private final HostelAllocationRepository allocations = mock(HostelAllocationRepository.class);
    private final StudentProfileRepository studentProfiles = mock(StudentProfileRepository.class);
    private final ApprovalQueryService approvals = mock(ApprovalQueryService.class);
    private final HostelQueryService service = new HostelQueryService(applications, allocations, studentProfiles, approvals);

    private final UserAccount studentAccount = HostelFixtures.account(70, "student1", RoleName.STUDENT);
    private final UserAccount admin = HostelFixtures.account(1, "admin1", RoleName.ADMIN);
    private final StudentProfile student = HostelFixtures.student(5, studentAccount);
    private final ApprovalRequest pending = HostelFixtures.approval(12, studentAccount);
    private HostelApplication application;

    @BeforeEach
    void setUp() {
        application = HostelFixtures.application(42, student, HostelFixtures.session(2, "2026-27", 2026), pending);
        when(applications.findWithContextById(42L)).thenReturn(Optional.of(application));
        when(allocations.findAllForApplications(any())).thenReturn(List.of());
        when(approvals.requestDetail(12L)).thenReturn(Optional.empty());
        when(approvals.currentStep(12L)).thenReturn(Optional.of(new CurrentApprovalStep(121L, 1, "Administrative Approval", "ADMIN")));
    }

    @Test
    void aStudentSeesOnlyTheirOwnApplication() {
        HostelApplicationDetail detail = service.studentApplication(70L, 42L);
        assertThat(detail.applicationNumber()).isEqualTo("HST-000042");
        assertThat(detail.approvalStatus()).isEqualTo("PENDING");
        assertThat(detail.canCancel()).isTrue();
        assertThat(detail.activeAllocation()).isNull();

        assertThatThrownBy(() -> service.studentApplication(71L, 42L)).hasMessage(HostelApplicationService.NOT_FOUND);
        assertThatThrownBy(() -> service.studentApplication(70L, 404L)).hasMessage(HostelApplicationService.NOT_FOUND);
    }

    @Test
    void theActiveAllocationIsShownAndCancellingIsNoLongerOffered() {
        HostelApplication approved = HostelFixtures.application(43, student, HostelFixtures.session(2, "2026-27", 2026),
                HostelFixtures.approved(13, studentAccount, admin));
        when(applications.findWithContextById(43L)).thenReturn(Optional.of(approved));
        HostelBed bed = HostelFixtures.bed(3, HostelFixtures.room(2, HostelFixtures.hostel(1, "MAIN"), "A-101"), "B1");
        HostelAllocation old = new HostelAllocation(approved, HostelFixtures.bed(4, bed.getRoom(), "B2"), admin, HostelFixtures.NOW);
        old.vacate(admin, HostelFixtures.NOW);
        when(allocations.findAllForApplications(List.of(43L)))
                .thenReturn(List.of(old, new HostelAllocation(approved, bed, admin, HostelFixtures.NOW)));

        HostelApplicationDetail detail = service.studentApplication(70L, 43L);

        assertThat(detail.canCancel()).isFalse();
        assertThat(detail.activeAllocation().bedNumber()).isEqualTo("B1");
        assertThat(detail.pastAllocations()).singleElement().satisfies(past -> assertThat(past.status()).isEqualTo("VACATED"));
    }

    @Test
    void theOfficeMayDecideOnlyWithTheStepsRoleAndThePermissionAndNeverAsTheRequester() {
        ApprovalActor decider = HostelFixtures.actor(1, "ROLE_ADMIN", ApprovalActor.TASK_DECIDE, "HOSTEL_MANAGE");
        ApprovalActor managerOnly = HostelFixtures.actor(1, "ROLE_ADMIN", "HOSTEL_MANAGE");
        ApprovalActor requester = HostelFixtures.actor(70, "ROLE_STUDENT", "ROLE_ADMIN", ApprovalActor.TASK_DECIDE, "HOSTEL_MANAGE");

        HostelAdminApplicationDetail detail = service.adminApplication(decider, 42L);
        assertThat(detail.studentLoginId()).isEqualTo("student1");
        assertThat(detail.currentApprovalStep().approvalRequestStepId()).isEqualTo(121L);
        assertThat(detail.canDecideCurrentStep()).isTrue();
        assertThat(detail.canAllocate()).isFalse();
        assertThat(service.adminApplication(managerOnly, 42L).canDecideCurrentStep()).isFalse();
        assertThat(service.adminApplication(requester, 42L).canDecideCurrentStep()).isFalse();
    }

    @Test
    void anApprovedApplicationWithoutAnActiveAllocationCanBeAllocated() {
        HostelApplication approved = HostelFixtures.application(43, student, HostelFixtures.session(2, "2026-27", 2026),
                HostelFixtures.approved(13, studentAccount, admin));
        when(applications.findWithContextById(43L)).thenReturn(Optional.of(approved));
        when(approvals.currentStep(13L)).thenReturn(Optional.empty());

        HostelAdminApplicationDetail detail = service.adminApplication(HostelFixtures.actor(1, "ROLE_ADMIN"), 43L);
        assertThat(detail.approvalStatus()).isEqualTo("APPROVED");
        assertThat(detail.canAllocate()).isTrue();
        assertThat(detail.currentApprovalStep()).isNull();
    }

    @Test
    void pagesAreChecked() {
        assertThatThrownBy(() -> service.adminApplications(null, null, null, null, 0, 101))
                .hasMessage("The page size must be between 1 and 100.");
        assertThatThrownBy(() -> service.allocations(null, null, null, null, -1, 20)).hasMessage("The page must be 0 or more.");
    }
}
