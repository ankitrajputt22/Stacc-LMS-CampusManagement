package com.stacc.backend.campus.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.approval.ApprovalCommands.ApprovalStartResult;
import com.stacc.backend.approval.ApprovalCommands.StartApprovalCommand;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalRequest;
import com.stacc.backend.approval.ApprovalRequestRepository;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.campus.CampusFixtures;
import com.stacc.backend.campus.CampusNotifications;
import com.stacc.backend.campus.services.CampusServiceInputs.CreateCampusServiceRequestInput;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class CampusServiceRequestServiceTest {

    private final CampusServiceRequestRepository requests = mock(CampusServiceRequestRepository.class);
    private final CampusServiceDefinitionRepository services = mock(CampusServiceDefinitionRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final ApprovalWorkflowService approvals = mock(ApprovalWorkflowService.class);
    private final ApprovalQueryService approvalQueries = mock(ApprovalQueryService.class);
    private final ApprovalRequestRepository approvalRequests = mock(ApprovalRequestRepository.class);
    private final CampusServiceQueryService queries = mock(CampusServiceQueryService.class);
    private final CampusNotifications notifications = mock(CampusNotifications.class);
    private final CampusServiceRequestService service = new CampusServiceRequestService(requests, services, accounts,
            approvals, approvalQueries, approvalRequests, queries, notifications,
            Clock.fixed(CampusFixtures.NOW, ZoneOffset.UTC));

    private final UserAccount student = CampusFixtures.account(70, "student1", RoleName.STUDENT);
    private final UserAccount admin = CampusFixtures.account(1, "admin1", RoleName.ADMIN);
    private final CampusServiceDefinition idCard = CampusFixtures.service(1, "ID_CARD", true, false, "CAMPUS_SERVICE_REQUEST");
    private final CampusServiceDefinition general = CampusFixtures.service(2, "GENERAL_ADMIN", true, true, null);
    private final ApprovalRequest pending = CampusFixtures.approval(12, student);
    private final ApprovalActor adminActor = CampusFixtures.approvalActor(1, "ROLE_ADMIN", "CAMPUS_SERVICE_MANAGE");

    @BeforeEach
    void setUp() {
        when(services.findById(1L)).thenReturn(Optional.of(idCard));
        when(services.findById(2L)).thenReturn(Optional.of(general));
        when(accounts.findById(70L)).thenReturn(Optional.of(student));
        when(requests.saveAndFlush(any())).thenAnswer(call -> {
            CampusServiceRequest saved = call.getArgument(0);
            return saved.getId() == null ? CampusFixtures.withId(saved, 42) : saved;
        });
        when(approvalQueries.isStartableWorkflow("CAMPUS_SERVICE_REQUEST")).thenReturn(true);
        when(approvals.startApproval(any())).thenReturn(new ApprovalStartResult(12L, ApprovalRequestStatus.PENDING));
        when(approvalRequests.getReferenceById(12L)).thenReturn(pending);
    }

    private static void assertRefused(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    private CampusServiceRequest stored(CampusServiceRequest request) {
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        return request;
    }

    // Creating

    @Test
    void aServiceThatNeedsApprovalStartsSubmittedWithItsApprovalLinked() {
        ArgumentCaptor<CampusServiceRequest> saved = ArgumentCaptor.forClass(CampusServiceRequest.class);

        service.create(CampusFixtures.actor(70, "ROLE_STUDENT"),
                new CreateCampusServiceRequestInput(1L, "Lost card", "I lost my ID card."));

        ArgumentCaptor<StartApprovalCommand> command = ArgumentCaptor.forClass(StartApprovalCommand.class);
        verify(approvals).startApproval(command.capture());
        assertThat(command.getValue().workflowCode()).isEqualTo("CAMPUS_SERVICE_REQUEST");
        assertThat(command.getValue().requesterUserAccountId()).isEqualTo(70L);
        assertThat(command.getValue().sourceType()).isEqualTo("CAMPUS_SERVICE_REQUEST");
        assertThat(command.getValue().sourceId()).isEqualTo("42");
        assertThat(command.getValue().subject()).contains("CSR-000042");
        verify(requests, times(2)).saveAndFlush(saved.capture());
        CampusServiceRequest request = saved.getValue();
        assertThat(request.getStatus()).isEqualTo(CampusServiceRequestStatus.SUBMITTED);
        assertThat(request.getApprovalRequest()).isSameAs(pending);
        assertThat(request.getRequester()).isSameAs(student);
        assertThat(request.getRequestNumber()).isEqualTo("CSR-000042");
        verify(queries).ownRequest(70L, 42L);
    }

    @Test
    void aServiceWithoutApprovalStartsApprovedWithNoApproval() {
        ArgumentCaptor<CampusServiceRequest> saved = ArgumentCaptor.forClass(CampusServiceRequest.class);

        service.create(CampusFixtures.actor(70, "ROLE_STUDENT"),
                new CreateCampusServiceRequestInput(2L, "Bonafide letter", "For a scholarship."));

        verify(requests, times(2)).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CampusServiceRequestStatus.APPROVED);
        assertThat(saved.getValue().getApprovalRequest()).isNull();
        verify(approvals, never()).startApproval(any());
        verify(notifications, never()).serviceCompleted(any());
    }

    @Test
    void aServiceMustExistBeActiveAndBeAvailableToOneOfTheCallersRoles() {
        assertRefused(() -> service.create(CampusFixtures.actor(70, "ROLE_STUDENT"),
                new CreateCampusServiceRequestInput(99L, "S", "D")), HttpStatus.NOT_FOUND,
                CampusServiceRequestService.SERVICE_NOT_FOUND);
        assertRefused(() -> service.create(CampusFixtures.actor(80, "ROLE_FACULTY"),
                new CreateCampusServiceRequestInput(1L, "S", "D")), HttpStatus.CONFLICT,
                CampusServiceRequestService.SERVICE_UNAVAILABLE);
        assertRefused(() -> service.create(CampusFixtures.actor(1, "ROLE_ADMIN"),
                new CreateCampusServiceRequestInput(2L, "S", "D")), HttpStatus.CONFLICT,
                CampusServiceRequestService.SERVICE_UNAVAILABLE);
        general.update(new CampusServiceDefinition.Settings("General", null, false, true, true, false, null));
        assertRefused(() -> service.create(CampusFixtures.actor(70, "ROLE_STUDENT"),
                new CreateCampusServiceRequestInput(2L, "S", "D")), HttpStatus.CONFLICT,
                CampusServiceRequestService.SERVICE_INACTIVE);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void aMultiRoleAccountMayUseAServiceThroughEitherRole() {
        service.create(CampusFixtures.actor(70, "ROLE_FACULTY", "ROLE_STUDENT"),
                new CreateCampusServiceRequestInput(1L, "S", "D"));
        verify(approvals).startApproval(any());
    }

    @Test
    void anInactiveWorkflowRefusesNewRequestsUntilCorrected() {
        when(approvalQueries.isStartableWorkflow("CAMPUS_SERVICE_REQUEST")).thenReturn(false);

        assertRefused(() -> service.create(CampusFixtures.actor(70, "ROLE_STUDENT"),
                new CreateCampusServiceRequestInput(1L, "S", "D")), HttpStatus.CONFLICT,
                CampusServiceRequestService.APPROVAL_UNAVAILABLE);
        verify(requests, never()).saveAndFlush(any());
        verify(approvals, never()).startApproval(any());
    }

    @Test
    void anEngineRefusalAtTheLastMomentIsAPlain409() {
        when(approvals.startApproval(any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "inactive"));

        assertRefused(() -> service.create(CampusFixtures.actor(70, "ROLE_STUDENT"),
                new CreateCampusServiceRequestInput(1L, "S", "D")), HttpStatus.CONFLICT,
                CampusServiceRequestService.APPROVAL_UNAVAILABLE);
    }

    @Test
    void anInvalidSubjectIs400() {
        assertThatThrownBy(() -> service.create(CampusFixtures.actor(70, "ROLE_STUDENT"),
                new CreateCampusServiceRequestInput(2L, " ", "D")))
                .isInstanceOfSatisfying(ApiException.class,
                        refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // Cancelling

    @Test
    void cancellingAPendingRequestCancelsItsApprovalInTheEngine() {
        CampusServiceRequest request = stored(CampusFixtures.request(42, idCard, student, pending));
        when(approvals.cancelApprovalIfPending(12L, 70L)).thenAnswer(call -> {
            pending.cancel(CampusFixtures.NOW);
            return true;
        });

        service.cancel(70L, 42L);

        verify(approvals).cancelApprovalIfPending(12L, 70L);
        assertThat(request.getStatus()).isEqualTo(CampusServiceRequestStatus.CANCELLED);
        verify(requests).saveAndFlush(request);
        verify(queries).ownRequest(70L, 42L);
    }

    @Test
    void cancellingARequestWithoutApprovalNeedsNoEngine() {
        CampusServiceRequest request = stored(CampusFixtures.request(43, general, student, null));

        service.cancel(70L, 43L);

        assertThat(request.getStatus()).isEqualTo(CampusServiceRequestStatus.CANCELLED);
        verify(approvals, never()).cancelApprovalIfPending(anyLong(), any());
    }

    @Test
    void aDecisionMadeFirstWins() {
        ApprovalRequest rejected = CampusFixtures.rejected(13, student, admin, "Not eligible.");
        CampusServiceRequest lost = stored(CampusFixtures.request(44, idCard, student, rejected));
        when(approvals.cancelApprovalIfPending(13L, 70L)).thenReturn(false);

        assertRefused(() -> service.cancel(70L, 44L), HttpStatus.CONFLICT, CampusServiceRequestService.CANNOT_CANCEL);
        assertThat(lost.getStatus()).isEqualTo(CampusServiceRequestStatus.REJECTED);

        // Approved first: the request is accepted for handling and may still be cancelled before completion.
        ApprovalRequest approved = CampusFixtures.approved(14, student, admin);
        CampusServiceRequest open = stored(CampusFixtures.request(45, idCard, student, approved));
        when(approvals.cancelApprovalIfPending(14L, 70L)).thenReturn(false);
        service.cancel(70L, 45L);
        assertThat(open.getStatus()).isEqualTo(CampusServiceRequestStatus.CANCELLED);
        assertThat(approved.getStatus()).isEqualTo(ApprovalRequestStatus.APPROVED);
    }

    @Test
    void completedRejectedAndCancelledRequestsCannotBeCancelled() {
        CampusServiceRequest completed = stored(CampusFixtures.request(46, general, student, null));
        completed.complete(CampusFixtures.NOW);
        CampusServiceRequest cancelled = stored(CampusFixtures.request(47, general, student, null));
        cancelled.cancel();

        assertRefused(() -> service.cancel(70L, 46L), HttpStatus.CONFLICT, CampusServiceRequestService.CANNOT_CANCEL);
        assertRefused(() -> service.cancel(70L, 47L), HttpStatus.CONFLICT, CampusServiceRequestService.CANNOT_CANCEL);
        assertThat(completed.getStatus()).isEqualTo(CampusServiceRequestStatus.COMPLETED);
    }

    @Test
    void anotherAccountsRequestIsTheSame404AsAMissingOne() {
        stored(CampusFixtures.request(43, general, student, null));

        assertRefused(() -> service.cancel(71L, 43L), HttpStatus.NOT_FOUND, CampusServiceRequestService.NOT_FOUND);
        assertRefused(() -> service.cancel(70L, 999L), HttpStatus.NOT_FOUND, CampusServiceRequestService.NOT_FOUND);
        verify(approvals, never()).cancelApprovalIfPending(anyLong(), any());
    }

    // Completing

    @Test
    void anApprovedRequestCompletesOnceAndTellsTheRequester() {
        CampusServiceRequest request = stored(CampusFixtures.request(43, general, student, null));

        service.complete(adminActor, 43L);
        service.complete(adminActor, 43L);

        assertThat(request.getStatus()).isEqualTo(CampusServiceRequestStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isEqualTo(CampusFixtures.NOW.truncatedTo(ChronoUnit.MICROS));
        verify(notifications, times(1)).serviceCompleted(request);
        verify(queries, times(2)).adminRequest(adminActor, 43L);
    }

    @Test
    void anApprovalApprovedInTheEngineIsSynchronizedBeforeCompleting() {
        CampusServiceRequest request = stored(CampusFixtures.request(42, idCard, student,
                CampusFixtures.approved(12, student, admin)));

        service.complete(adminActor, 42L);

        assertThat(request.getStatus()).isEqualTo(CampusServiceRequestStatus.COMPLETED);
        verify(notifications).serviceCompleted(request);
    }

    @Test
    void submittedRejectedAndCancelledRequestsCannotBeCompleted() {
        stored(CampusFixtures.request(42, idCard, student, pending));
        CampusServiceRequest rejected = stored(CampusFixtures.request(43, idCard, student,
                CampusFixtures.rejected(13, student, admin, "No.")));
        CampusServiceRequest cancelled = stored(CampusFixtures.request(44, general, student, null));
        cancelled.cancel();

        for (long id : new long[] {42, 43, 44}) {
            assertRefused(() -> service.complete(adminActor, id), HttpStatus.CONFLICT,
                    CampusServiceRequestService.CANNOT_COMPLETE);
        }
        // The rejection reached the business status; the approval was never touched.
        assertThat(rejected.getStatus()).isEqualTo(CampusServiceRequestStatus.REJECTED);
        assertThat(pending.getStatus()).isEqualTo(ApprovalRequestStatus.PENDING);
        verify(notifications, never()).serviceCompleted(any());
        assertRefused(() -> service.complete(adminActor, 999L), HttpStatus.NOT_FOUND, CampusServiceRequestService.NOT_FOUND);
    }

    @Test
    void thereIsNoCampusApprovalShortcut() {
        assertThat(CampusServiceRequestService.class.getMethods()).noneMatch(method ->
                method.getName().startsWith("approve") || method.getName().startsWith("reject"));
        verify(approvals, never()).approveStep(any(), any(), any());
        verify(approvals, never()).rejectStep(any(), any(), eq("x"));
    }
}
