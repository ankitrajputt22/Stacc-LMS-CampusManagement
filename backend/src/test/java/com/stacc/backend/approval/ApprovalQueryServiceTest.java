package com.stacc.backend.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestPage;
import com.stacc.backend.approval.ApprovalResponses.ApprovalTaskPage;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class ApprovalQueryServiceTest {

    private final ApprovalWorkflowDefinitionRepository workflows = mock(ApprovalWorkflowDefinitionRepository.class);
    private final ApprovalRequestRepository requests = mock(ApprovalRequestRepository.class);
    private final ApprovalRequestStepRepository steps = mock(ApprovalRequestStepRepository.class);
    private final ApprovalRequestHistoryRepository history = mock(ApprovalRequestHistoryRepository.class);
    private final ApprovalQueryService service =
            new ApprovalQueryService(workflows, requests, steps, history, new ApprovalAccessService());

    private final ApprovalRequest request = ApprovalFixtures.request(12,
            ApprovalFixtures.workflow(4, "TWO_STEP", RoleName.FACULTY, RoleName.ADMIN),
            ApprovalFixtures.account(70, "student1", RoleName.STUDENT));

    private static void assertRefused(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, refusal -> assertThat(refusal.getStatus()).isEqualTo(status));
    }

    @Test
    void ownRequestsAreTheTokenAccountsNewestFirstWithoutAccountIds() {
        when(requests.findOwn(eq(70L), eq(ApprovalRequestStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(1, 5), 6));

        ApprovalRequestPage page = service.ownRequests(ApprovalFixtures.actor(70, "ROLE_STUDENT",
                ApprovalActor.REQUEST_VIEW_OWN), ApprovalRequestStatus.PENDING, 1, 5);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.workflowCode()).isEqualTo("TWO_STEP");
            assertThat(item.subject()).isEqualTo("Demo approval 12");
            assertThat(item.currentStepOrder()).isEqualTo(1);
        });
        assertThat(page.totalPages()).isEqualTo(2);
        verify(requests).findOwn(70L, ApprovalRequestStatus.PENDING, PageRequest.of(1, 5));
    }

    @Test
    void anotherAccountsRequestAnswersTheSame404AsAMissingOne() {
        when(requests.findWithContextById(12L)).thenReturn(Optional.of(request));
        when(steps.findAllWithActorByRequestId(12L)).thenReturn(request.getSteps());
        when(history.findAllWithActorByRequestId(12L)).thenReturn(List.of());

        assertThat(service.ownRequest(ApprovalFixtures.actor(70, ApprovalActor.REQUEST_VIEW_OWN), 12L).steps()).hasSize(2);
        for (ApprovalActor other : List.of(ApprovalFixtures.actor(71, ApprovalActor.REQUEST_VIEW_OWN),
                ApprovalFixtures.approver(1, RoleName.ADMIN, RoleName.FACULTY), ApprovalFixtures.actor(70))) {
            assertThatThrownBy(() -> service.ownRequest(other, 12L)).hasMessage(ApprovalWorkflowService.REQUEST_NOT_FOUND);
        }
        assertThatThrownBy(() -> service.ownRequest(ApprovalFixtures.actor(70, ApprovalActor.REQUEST_VIEW_OWN), 404L))
                .hasMessage(ApprovalWorkflowService.REQUEST_NOT_FOUND);
    }

    @Test
    void theTaskQueueAsksForTheTokensRolesAndNeverTheApproversOwnRequests() {
        when(steps.findTasks(any(), any(), any())).thenReturn(new PageImpl<>(List.of(request.getSteps().get(0))));

        ApprovalTaskPage page = service.tasks(ApprovalFixtures.approver(2, RoleName.ADMIN, RoleName.FACULTY), 0, 20);

        assertThat(page.items()).singleElement().satisfies(task -> {
            assertThat(task.approvalRequestStepId()).isEqualTo(121L);
            assertThat(task.requiredRoleCode()).isEqualTo("FACULTY");
            assertThat(task.requesterLoginId()).isEqualTo("student1");
        });
        verify(steps).findTasks(Set.of("ADMIN", "FACULTY"), 2L, PageRequest.of(0, 20));
    }

    @Test
    void theQueueNeedsThePermissionAndValidPaging() {
        assertRefused(() -> service.tasks(ApprovalFixtures.actor(80, "ROLE_FACULTY"), 0, 20), HttpStatus.FORBIDDEN);
        assertRefused(() -> service.tasks(ApprovalFixtures.approver(80, RoleName.FACULTY), 0, 101), HttpStatus.BAD_REQUEST);
        assertRefused(() -> service.tasks(ApprovalFixtures.approver(80, RoleName.FACULTY), -1, 20), HttpStatus.BAD_REQUEST);
        assertRefused(() -> service.ownRequests(ApprovalFixtures.actor(70), null, 0, 0), HttpStatus.BAD_REQUEST);
        assertThat(service.tasks(ApprovalFixtures.actor(5, ApprovalActor.TASK_VIEW), 0, 20).items()).isEmpty();
        verifyNoInteractions(steps, requests);
    }

    @Test
    void businessModulesCanLookUpOutcomes() {
        when(requests.findWithContextById(12L)).thenReturn(Optional.of(request));
        when(requests.findAllForSource("DEMO_SOURCE", "source-12")).thenReturn(List.of(request));
        when(requests.existsPendingForSource("DEMO_SOURCE", "source-12", "TWO_STEP")).thenReturn(true);

        assertThat(service.requestStatus(12L)).get().satisfies(outcome -> {
            assertThat(outcome.status()).isEqualTo(ApprovalRequestStatus.PENDING);
            assertThat(outcome.workflowCode()).isEqualTo("TWO_STEP");
        });
        assertThat(service.requestStatus(null)).isEmpty();
        assertThat(service.requestsForSource("DEMO_SOURCE", "source-12")).hasSize(1);
        assertThat(service.hasPendingApproval("DEMO_SOURCE", "source-12", " two_step ")).isTrue();
    }
}
