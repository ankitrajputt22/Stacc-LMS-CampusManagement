package com.stacc.backend.campus.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalCommands.CurrentApprovalStep;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestDetail;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestStepResponse;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.campus.CampusFixtures;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class CampusServiceQueryServiceTest {

    private final CampusServiceRequestRepository requests = mock(CampusServiceRequestRepository.class);
    private final ApprovalQueryService approvals = mock(ApprovalQueryService.class);
    private final CampusServiceQueryService service = new CampusServiceQueryService(requests, approvals);

    private final UserAccount student = CampusFixtures.account(70, "student1", RoleName.STUDENT);
    private final UserAccount admin = CampusFixtures.account(1, "admin1", RoleName.ADMIN);
    private final CampusServiceDefinition idCard = CampusFixtures.service(1, "ID_CARD", true, false, "CAMPUS_SERVICE_REQUEST");
    private final CampusServiceDefinition general = CampusFixtures.service(2, "GENERAL_ADMIN", true, true, null);

    private static ApprovalRequestDetail detail(long id, String status, ApprovalRequestStepResponse... steps) {
        return new ApprovalRequestDetail(id, "CAMPUS_SERVICE_REQUEST", "Campus Service Request Approval",
                "CAMPUS_SERVICE_REQUEST", "42", "Campus service request", status, null, CampusFixtures.NOW, null,
                List.of(steps), List.of());
    }

    private static ApprovalRequestStepResponse step(String status, String comment) {
        return new ApprovalRequestStepResponse(1, "Administrative Approval", "ADMIN", status,
                status.equals("PENDING") ? null : "admin1", status.equals("PENDING") ? null : Instant.EPOCH, comment);
    }

    @Test
    void ownRequestsAreAPageOfTheTokenAccountsRequestsWithEffectiveStatuses() {
        CampusServiceRequest approvedInEngine = CampusFixtures.request(42, idCard, student,
                CampusFixtures.approved(12, student, admin));
        when(requests.findOwnPage(70L, null, CampusServiceRequestStatus.APPROVED, ApprovalRequestStatus.APPROVED,
                PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(approvedInEngine), PageRequest.of(0, 20), 1));

        var page = service.ownRequests(70L, CampusServiceRequestStatus.APPROVED, null, 0, 20);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.requestNumber()).isEqualTo("CSR-000042");
            assertThat(item.status()).isEqualTo("APPROVED");
            assertThat(item.approvalStatus()).isEqualTo("APPROVED");
            assertThat(item.serviceName()).isEqualTo("Service");
        });
        assertThat(page.totalElements()).isEqualTo(1);
        // Reading stores nothing.
        assertThat(approvedInEngine.getStatus()).isEqualTo(CampusServiceRequestStatus.SUBMITTED);
    }

    @Test
    void submittedAndCompletedFiltersHaveNoApprovalEquivalent() {
        when(requests.findOwnPage(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.ownRequests(70L, CampusServiceRequestStatus.SUBMITTED, 2L, 1, 5);
        verify(requests).findOwnPage(70L, 2L, CampusServiceRequestStatus.SUBMITTED, null, PageRequest.of(1, 5));
        service.ownRequests(70L, null, null, 0, 100);
        verify(requests).findOwnPage(70L, null, null, null, PageRequest.of(0, 100));
    }

    @Test
    void pagesAreBounded() {
        for (int[] bad : new int[][] {{-1, 20}, {0, 0}, {0, 101}}) {
            assertThatThrownBy(() -> service.ownRequests(70L, null, null, bad[0], bad[1]))
                    .isInstanceOfSatisfying(ApiException.class,
                            refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
            assertThatThrownBy(() -> service.adminRequests(null, null, null, bad[0], bad[1]))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void anotherAccountsRequestIsTheSame404AsAMissingOne() {
        when(requests.findWithContextById(42L)).thenReturn(Optional.of(CampusFixtures.request(42, general, student, null)));

        for (long[] call : new long[][] {{71, 42}, {70, 99}}) {
            assertThatThrownBy(() -> service.ownRequest(call[0], call[1]))
                    .isInstanceOfSatisfying(ApiException.class, refusal -> {
                        assertThat(refusal.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(refusal.getMessage()).isEqualTo(CampusServiceRequestService.NOT_FOUND);
                    });
        }
        assertThat(service.ownRequest(70L, 42L).requestNumber()).isEqualTo("CSR-000042");
    }

    @Test
    void theOwnDetailShowsApprovalProgressAndTheRealRejectionComment() {
        CampusServiceRequest rejected = CampusFixtures.request(42, idCard, student,
                CampusFixtures.rejected(12, student, admin, "Photo missing."));
        when(requests.findWithContextById(42L)).thenReturn(Optional.of(rejected));
        when(approvals.requestDetail(12L)).thenReturn(Optional.of(detail(12, "REJECTED", step("REJECTED", "Photo missing."))));

        var detail = service.ownRequest(70L, 42L);

        assertThat(detail.status()).isEqualTo("REJECTED");
        assertThat(detail.approvalStatus()).isEqualTo("REJECTED");
        assertThat(detail.rejectionComment()).isEqualTo("Photo missing.");
        assertThat(detail.canCancel()).isFalse();
        assertThat(detail.approvalSteps()).singleElement().satisfies(step -> assertThat(step.status()).isEqualTo("REJECTED"));
    }

    @Test
    void aRequestWithoutApprovalHasNoApprovalPartsAndCanBeCancelledUntilDone() {
        when(requests.findWithContextById(43L)).thenReturn(Optional.of(CampusFixtures.request(43, general, student, null)));

        var detail = service.ownRequest(70L, 43L);

        assertThat(detail.status()).isEqualTo("APPROVED");
        assertThat(detail.approvalStatus()).isNull();
        assertThat(detail.rejectionComment()).isNull();
        assertThat(detail.approvalSteps()).isEmpty();
        assertThat(detail.canCancel()).isTrue();
        verify(approvals, never()).requestDetail(anyLong());
    }

    @Test
    void theAdminDetailOffersTheCurrentStepOnlyToAnAdminWhoMayDecideIt() {
        CampusServiceRequest request = CampusFixtures.request(42, idCard, student, CampusFixtures.approval(12, student));
        when(requests.findWithContextById(42L)).thenReturn(Optional.of(request));
        when(approvals.requestDetail(12L)).thenReturn(Optional.of(detail(12, "PENDING", step("PENDING", null))));
        when(approvals.currentStep(12L)).thenReturn(Optional.of(new CurrentApprovalStep(121L, 1,
                "Administrative Approval", "ADMIN")));

        var decider = service.adminRequest(CampusFixtures.approvalActor(1, "ROLE_ADMIN", "CAMPUS_SERVICE_MANAGE",
                "APPROVAL_TASK_DECIDE"), 42L);
        assertThat(decider.requesterLoginId()).isEqualTo("student1");
        assertThat(decider.currentApprovalStep().approvalRequestStepId()).isEqualTo(121L);
        assertThat(decider.canDecide()).isTrue();
        assertThat(decider.canComplete()).isFalse();
        assertThat(decider.status()).isEqualTo("SUBMITTED");

        // CAMPUS_SERVICE_MANAGE alone is no approval pass.
        assertThat(service.adminRequest(CampusFixtures.approvalActor(1, "ROLE_ADMIN", "CAMPUS_SERVICE_MANAGE"), 42L)
                .canDecide()).isFalse();
        // Nobody decides their own request.
        assertThat(service.adminRequest(CampusFixtures.approvalActor(70, "ROLE_ADMIN", "APPROVAL_TASK_DECIDE"), 42L)
                .canDecide()).isFalse();
    }

    @Test
    void anApprovedRequestCanBeCompletedFromTheAdminDetail() {
        when(requests.findWithContextById(43L)).thenReturn(Optional.of(CampusFixtures.request(43, general, student, null)));

        var detail = service.adminRequest(CampusFixtures.approvalActor(1, "ROLE_ADMIN", "CAMPUS_SERVICE_MANAGE"), 43L);

        assertThat(detail.canComplete()).isTrue();
        assertThat(detail.currentApprovalStep()).isNull();
        assertThat(detail.canDecide()).isFalse();
    }

    @Test
    void theAdminQueueFiltersByServiceStatusAndTrimmedLoginId() {
        when(requests.findQueue(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(
                CampusFixtures.request(43, general, student, null))));

        var page = service.adminRequests(2L, CampusServiceRequestStatus.REJECTED, "  student1 ", 0, 20);

        verify(requests).findQueue(2L, "student1", CampusServiceRequestStatus.REJECTED, ApprovalRequestStatus.REJECTED,
                PageRequest.of(0, 20));
        assertThat(page.items()).singleElement().satisfies(item -> assertThat(item.requesterLoginId()).isEqualTo("student1"));
        service.adminRequests(null, null, " ", 0, 20);
        verify(requests).findQueue(null, null, null, null, PageRequest.of(0, 20));
    }

    @Test
    void theRequesterViewNamesNoAccount() {
        assertThat(CampusServiceResponses.CampusRequestDetail.class.getRecordComponents())
                .noneMatch(part -> part.getName().toLowerCase().contains("account")
                        || part.getName().toLowerCase().contains("requester"));
    }
}
