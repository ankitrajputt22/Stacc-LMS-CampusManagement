package com.stacc.backend.approval;

import static com.stacc.backend.approval.ApprovalRequestHistoryAction.REQUEST_APPROVED;
import static com.stacc.backend.approval.ApprovalRequestHistoryAction.REQUEST_CANCELLED;
import static com.stacc.backend.approval.ApprovalRequestHistoryAction.REQUEST_CREATED;
import static com.stacc.backend.approval.ApprovalRequestHistoryAction.REQUEST_REJECTED;
import static com.stacc.backend.approval.ApprovalRequestHistoryAction.STEP_ACTIVATED;
import static com.stacc.backend.approval.ApprovalRequestHistoryAction.STEP_APPROVED;
import static com.stacc.backend.approval.ApprovalRequestHistoryAction.STEP_REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.stacc.backend.approval.ApprovalCommands.ApprovalStartResult;
import com.stacc.backend.approval.ApprovalCommands.StartApprovalCommand;
import com.stacc.backend.approval.ApprovalResponses.ApprovalDecisionResponse;
import com.stacc.backend.approval.ApprovalWorkflowDefinition.StepSpec;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApprovalWorkflowServiceTest {

    private final UserAccount student = ApprovalFixtures.account(70, "student1", RoleName.STUDENT);
    private final UserAccount faculty = ApprovalFixtures.account(80, "faculty1", RoleName.FACULTY);
    private final UserAccount admin = ApprovalFixtures.account(1, "admin1", RoleName.ADMIN);
    private final UserAccount facultyAdmin = ApprovalFixtures.account(2, "facultyadmin1", RoleName.FACULTY, RoleName.ADMIN);

    private final ApprovalWorkflowDefinitionRepository workflows = mock(ApprovalWorkflowDefinitionRepository.class);
    private final ApprovalRequestRepository requests = mock(ApprovalRequestRepository.class);
    private final ApprovalRequestStepRepository steps = mock(ApprovalRequestStepRepository.class);
    private final ApprovalRequestHistoryRepository history = mock(ApprovalRequestHistoryRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final ApprovalNotifications notifications = mock(ApprovalNotifications.class);
    private final ApprovalWorkflowService service = new ApprovalWorkflowService(workflows, requests, steps, history,
            accounts, new ApprovalAccessService(), notifications, Clock.fixed(ApprovalFixtures.NOW, ZoneOffset.UTC));

    private final List<ApprovalRequestHistory> trail = new ArrayList<>();
    private final Map<Long, ApprovalRequest> stored = new HashMap<>();
    private final AtomicLong ids = new AtomicLong(11);
    private ApprovalWorkflowDefinition twoStep;
    private ApprovalWorkflowDefinition threeStep;

    @BeforeEach
    void setUp() {
        twoStep = ApprovalFixtures.workflow(4, "TWO_STEP", RoleName.FACULTY, RoleName.ADMIN);
        threeStep = ApprovalFixtures.workflow(5, "THREE_STEP", RoleName.FACULTY, RoleName.ADMIN, RoleName.ADMIN);
        when(workflows.findWithStepsByCode("TWO_STEP")).thenReturn(Optional.of(twoStep));
        when(workflows.findWithStepsByCode("THREE_STEP")).thenReturn(Optional.of(threeStep));
        for (UserAccount account : List.of(student, faculty, admin, facultyAdmin)) {
            when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        }
        when(requests.saveAndFlush(any())).thenAnswer(call -> {
            ApprovalRequest request = ApprovalFixtures.saved(call.getArgument(0), ids);
            stored.put(request.getId(), request);
            return request;
        });
        when(requests.findByIdForUpdate(anyLong())).thenAnswer(call -> Optional.ofNullable(stored.get(call.<Long>getArgument(0))));
        when(steps.findRequestIdByStepId(anyLong())).thenAnswer(call -> {
            long stepId = call.getArgument(0);
            return stored.containsKey(stepId / 10) ? Optional.of(stepId / 10) : Optional.empty();
        });
        when(history.saveAndFlush(any())).thenAnswer(call -> {
            trail.add(call.getArgument(0));
            return call.getArgument(0);
        });
    }

    private ApprovalRequest start(String workflow, UserAccount requester) {
        ApprovalStartResult result = service.startApproval(new StartApprovalCommand(workflow, requester.getId(),
                "HOSTEL_APPLICATION", "57", "Hostel application for Semester 4"));
        return stored.get(result.approvalRequestId());
    }

    private static long stepId(ApprovalRequest request, int order) {
        return request.getSteps().get(order - 1).getId();
    }

    private List<ApprovalRequestHistoryAction> actions() {
        return trail.stream().map(ApprovalRequestHistory::getAction).toList();
    }

    private static void assertRefused(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, refusal -> assertThat(refusal.getStatus()).isEqualTo(status));
    }

    // Starting

    @Test
    void startingCreatesAPendingRequestWithASnapshotHistoryAndApproverNotice() {
        ApprovalRequest request = start("two_step", student);

        assertThat(request.getStatus()).isEqualTo(ApprovalRequestStatus.PENDING);
        assertThat(request.getCurrentStepOrder()).isEqualTo(1);
        assertThat(request.getRequester()).isSameAs(student);
        assertThat(request.getSteps()).extracting(ApprovalRequestStep::getStatus)
                .containsExactly(ApprovalRequestStepStatus.PENDING, ApprovalRequestStepStatus.WAITING);
        assertThat(actions()).containsExactly(REQUEST_CREATED, STEP_ACTIVATED);
        assertThat(trail.get(0).getActor()).isSameAs(student);
        assertThat(trail.get(1).getActor()).isNull();
        assertThat(trail.get(1).getStepOrder()).isEqualTo(1);
        verify(notifications).actionRequired(request, request.getSteps().get(0));
    }

    @Test
    void aMissingOrInactiveWorkflowOrRequesterStartsNothing() {
        assertRefused(() -> start("NOPE", student), HttpStatus.NOT_FOUND);
        twoStep.update(twoStep.getName(), null, false, List.of(new StepSpec("A", ApprovalFixtures.role(RoleName.ADMIN))));
        assertThatThrownBy(() -> start("TWO_STEP", student)).hasMessage(ApprovalWorkflowService.WORKFLOW_INACTIVE);
        assertRefused(() -> service.startApproval(new StartApprovalCommand("THREE_STEP", 999L, "T", "1", "S")),
                HttpStatus.NOT_FOUND);
        assertRefused(() -> service.startApproval(new StartApprovalCommand("THREE_STEP", 70L, "T", "1", " ")),
                HttpStatus.BAD_REQUEST);
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void editingTheWorkflowLaterChangesOnlyNewRequests() {
        ApprovalWorkflowDefinition oneStep = ApprovalFixtures.workflow(6, "ONE_STEP", RoleName.FACULTY);
        when(workflows.findWithStepsByCode("ONE_STEP")).thenReturn(Optional.of(oneStep));
        ApprovalRequest first = start("ONE_STEP", student);

        oneStep.update(oneStep.getName(), null, true, List.of(new StepSpec("Office", ApprovalFixtures.role(RoleName.ADMIN))));
        ApprovalRequest second = start("ONE_STEP", student);

        assertThat(first.getSteps()).extracting(ApprovalRequestStep::getRequiredRoleCode).containsExactly("FACULTY");
        assertThat(second.getSteps()).extracting(ApprovalRequestStep::getRequiredRoleCode).containsExactly("ADMIN");
        // The old request is still decided by FACULTY, and not by ADMIN.
        assertRefused(() -> service.approveStep(ApprovalFixtures.approver(1, RoleName.ADMIN), stepId(first, 1), null),
                HttpStatus.FORBIDDEN);
        assertThat(service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), stepId(first, 1), null)
                .requestStatus()).isEqualTo("APPROVED");
    }

    // Deciding

    @Test
    void approvingTheFirstStepActivatesTheSecondAndTellsItsApprovers() {
        ApprovalRequest request = start("TWO_STEP", student);
        trail.clear();

        ApprovalDecisionResponse result = service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY),
                stepId(request, 1), "Fine by me.");

        assertThat(result.stepStatus()).isEqualTo("APPROVED");
        assertThat(result.requestStatus()).isEqualTo("PENDING");
        assertThat(result.currentStepOrder()).isEqualTo(2);
        assertThat(request.getSteps().get(0).getActedBy()).isSameAs(faculty);
        assertThat(request.getSteps().get(1).getStatus()).isEqualTo(ApprovalRequestStepStatus.PENDING);
        assertThat(actions()).containsExactly(STEP_APPROVED, STEP_ACTIVATED);
        assertThat(trail.get(0).getActor()).isSameAs(faculty);
        assertThat(trail.get(0).getComment()).isEqualTo("Fine by me.");
        verify(notifications).actionRequired(request, request.getSteps().get(1));
        verify(notifications, never()).approved(any());
    }

    @Test
    void approvingTheLastStepApprovesTheRequestAndTellsTheRequester() {
        ApprovalRequest request = start("TWO_STEP", student);
        service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), stepId(request, 1), null);
        trail.clear();

        ApprovalDecisionResponse result = service.approveStep(ApprovalFixtures.approver(1, RoleName.ADMIN),
                stepId(request, 2), null);

        assertThat(result.requestStatus()).isEqualTo("APPROVED");
        assertThat(result.currentStepOrder()).isNull();
        assertThat(request.getCompletedAt()).isNotNull();
        assertThat(actions()).containsExactly(STEP_APPROVED, REQUEST_APPROVED);
        verify(notifications).approved(request);
    }

    @Test
    void rejectingTheSecondOfThreeStepsRejectsTheRequestAndSkipsTheThird() {
        ApprovalRequest request = start("THREE_STEP", student);
        service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), stepId(request, 1), null);
        trail.clear();

        ApprovalDecisionResponse result = service.rejectStep(ApprovalFixtures.approver(1, RoleName.ADMIN),
                stepId(request, 2), "Fees are not yet paid.");

        assertThat(result.stepStatus()).isEqualTo("REJECTED");
        assertThat(result.requestStatus()).isEqualTo("REJECTED");
        assertThat(request.getSteps()).extracting(ApprovalRequestStep::getStatus).containsExactly(
                ApprovalRequestStepStatus.APPROVED, ApprovalRequestStepStatus.REJECTED, ApprovalRequestStepStatus.SKIPPED);
        assertThat(request.getCurrentStepOrder()).isNull();
        assertThat(request.getCompletedAt()).isNotNull();
        assertThat(actions()).containsExactly(STEP_REJECTED, REQUEST_REJECTED);
        assertThat(trail.get(0).getComment()).isEqualTo("Fees are not yet paid.");
        verify(notifications).rejected(request);
    }

    @Test
    void rejectingNeedsAComment() {
        ApprovalRequest request = start("TWO_STEP", student);

        for (String blank : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> service.rejectStep(ApprovalFixtures.approver(80, RoleName.FACULTY),
                    stepId(request, 1), blank)).hasMessage(ApprovalWorkflowService.COMMENT_REQUIRED);
        }
        assertThat(request.getStatus()).isEqualTo(ApprovalRequestStatus.PENDING);
        assertThat(service.rejectStep(ApprovalFixtures.approver(80, RoleName.FACULTY), stepId(request, 1), "No room left.")
                .requestStatus()).isEqualTo("REJECTED");
    }

    // Who may decide

    @Test
    void onlyAnAccountHoldingTheStepsRoleWithThePermissionDecides() {
        ApprovalRequest request = start("TWO_STEP", student);
        long facultyStep = stepId(request, 1);

        // ADMIN gets no pass on a FACULTY step.
        assertRefused(() -> service.approveStep(ApprovalFixtures.approver(1, RoleName.ADMIN), facultyStep, null),
                HttpStatus.FORBIDDEN);
        // A student has neither the role nor the permission.
        assertRefused(() -> service.approveStep(ApprovalFixtures.actor(71, "ROLE_STUDENT", ApprovalActor.REQUEST_VIEW_OWN),
                facultyStep, null), HttpStatus.FORBIDDEN);
        // FACULTY without APPROVAL_TASK_DECIDE cannot decide.
        assertRefused(() -> service.approveStep(ApprovalFixtures.actor(80, "ROLE_FACULTY", ApprovalActor.TASK_VIEW),
                facultyStep, null), HttpStatus.FORBIDDEN);
        assertThat(request.getSteps().get(0).getStatus()).isEqualTo(ApprovalRequestStepStatus.PENDING);

        assertThat(service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), facultyStep, null).stepStatus())
                .isEqualTo("APPROVED");
    }

    @Test
    void aRoleRemovedOrAnAccountDisabledAfterSignInDecidesNothing() {
        ApprovalRequest request = start("TWO_STEP", student);
        UserAccount formerFaculty = ApprovalFixtures.account(81, "faculty2");
        when(accounts.findById(81L)).thenReturn(Optional.of(formerFaculty));
        assertRefused(() -> service.approveStep(ApprovalFixtures.approver(81, RoleName.FACULTY), stepId(request, 1), null),
                HttpStatus.FORBIDDEN);

        faculty.setStatus(AccountStatus.DISABLED);
        assertRefused(() -> service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), stepId(request, 1), null),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void theRequesterNeverDecidesTheirOwnRequest() {
        ApprovalRequest request = start("TWO_STEP", faculty);

        assertThatThrownBy(() -> service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), stepId(request, 1),
                null)).hasMessage(ApprovalAccessService.OWN_REQUEST);
    }

    @Test
    void anAccountWithBothRolesDecidesBothStepsWhateverTheRoleOrder() {
        ApprovalRequest request = start("TWO_STEP", student);
        ApprovalActor bothRoles = ApprovalFixtures.approver(2, RoleName.ADMIN, RoleName.FACULTY);

        service.approveStep(bothRoles, stepId(request, 1), null);
        assertThat(service.approveStep(ApprovalFixtures.approver(2, RoleName.FACULTY, RoleName.ADMIN), stepId(request, 2), null)
                .requestStatus()).isEqualTo("APPROVED");
        assertThat(request.getSteps()).extracting(step -> step.getActedBy().getLoginId())
                .containsExactly("facultyadmin1", "facultyadmin1");
    }

    // Stale, finished, and unknown

    @Test
    void aSecondDecisionOnTheSameStepIsAConflictAndNotifiesNobodyAgain() {
        ApprovalRequest request = start("TWO_STEP", student);
        long first = stepId(request, 1);
        service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), first, null);
        int entries = trail.size();

        assertThatThrownBy(() -> service.approveStep(ApprovalFixtures.approver(2, RoleName.FACULTY), first, null))
                .hasMessage(ApprovalWorkflowService.STEP_DECIDED);
        assertThatThrownBy(() -> service.rejectStep(ApprovalFixtures.approver(2, RoleName.FACULTY), first, "Late."))
                .hasMessage(ApprovalWorkflowService.STEP_DECIDED);

        assertThat(trail).hasSize(entries);
        verify(notifications, times(1)).actionRequired(request, request.getSteps().get(1));
    }

    @Test
    void aWaitingStepCannotBeDecidedYet() {
        ApprovalRequest request = start("TWO_STEP", student);

        assertThatThrownBy(() -> service.approveStep(ApprovalFixtures.approver(1, RoleName.ADMIN), stepId(request, 2), null))
                .hasMessage(ApprovalWorkflowService.STEP_NOT_ACTIVE);
    }

    @Test
    void finishedRequestsCannotContinue() {
        ApprovalRequest approved = start("TWO_STEP", student);
        service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), stepId(approved, 1), null);
        service.approveStep(ApprovalFixtures.approver(1, RoleName.ADMIN), stepId(approved, 2), null);
        ApprovalRequest rejected = start("THREE_STEP", student);
        service.rejectStep(ApprovalFixtures.approver(80, RoleName.FACULTY), stepId(rejected, 1), "No.");
        ApprovalRequest cancelled = start("TWO_STEP", student);
        service.cancelApproval(cancelled.getId(), null);

        assertRefused(() -> service.rejectStep(ApprovalFixtures.approver(1, RoleName.ADMIN), stepId(approved, 2), "No."),
                HttpStatus.CONFLICT);
        assertRefused(() -> service.approveStep(ApprovalFixtures.approver(1, RoleName.ADMIN), stepId(rejected, 2), null),
                HttpStatus.CONFLICT);
        assertRefused(() -> service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), stepId(cancelled, 1), null),
                HttpStatus.CONFLICT);
        assertRefused(() -> service.cancelApproval(approved.getId(), null), HttpStatus.CONFLICT);
    }

    @Test
    void anUnknownStepIs404() {
        assertRefused(() -> service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), 99_999L, null),
                HttpStatus.NOT_FOUND);
        assertRefused(() -> service.rejectStep(ApprovalFixtures.approver(80, RoleName.FACULTY), null, "x"),
                HttpStatus.NOT_FOUND);
        assertRefused(() -> service.cancelApproval(404L, null), HttpStatus.NOT_FOUND);
    }

    @Test
    void everyChangeLocksTheRequestRowFirst() {
        ApprovalRequest request = start("TWO_STEP", student);
        service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), stepId(request, 1), null);
        service.cancelApproval(request.getId(), 70L);

        verify(requests, times(2)).findByIdForUpdate(request.getId());
    }

    // Cancelling

    @Test
    void cancellingSkipsWhatIsLeftKeepsDecidedStepsAndRecordsWhoCancelled() {
        ApprovalRequest request = start("THREE_STEP", student);
        service.approveStep(ApprovalFixtures.approver(80, RoleName.FACULTY), stepId(request, 1), null);
        trail.clear();

        service.cancelApproval(request.getId(), 70L);

        assertThat(request.getStatus()).isEqualTo(ApprovalRequestStatus.CANCELLED);
        assertThat(request.getSteps()).extracting(ApprovalRequestStep::getStatus).containsExactly(
                ApprovalRequestStepStatus.APPROVED, ApprovalRequestStepStatus.SKIPPED, ApprovalRequestStepStatus.SKIPPED);
        assertThat(request.getCompletedAt()).isNotNull();
        assertThat(request.getCurrentStepOrder()).isNull();
        assertThat(actions()).containsExactly(REQUEST_CANCELLED);
        assertThat(trail.get(0).getActor()).isSameAs(student);
        verify(notifications).cancelled(request, 70L);
    }
}
