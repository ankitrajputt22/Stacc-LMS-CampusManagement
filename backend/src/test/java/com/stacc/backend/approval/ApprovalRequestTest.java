package com.stacc.backend.approval;

import static com.stacc.backend.approval.ApprovalRequestStepStatus.APPROVED;
import static com.stacc.backend.approval.ApprovalRequestStepStatus.PENDING;
import static com.stacc.backend.approval.ApprovalRequestStepStatus.REJECTED;
import static com.stacc.backend.approval.ApprovalRequestStepStatus.SKIPPED;
import static com.stacc.backend.approval.ApprovalRequestStepStatus.WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.approval.ApprovalWorkflowDefinition.StepSpec;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import org.junit.jupiter.api.Test;

class ApprovalRequestTest {

    private static final Instant LATER = Instant.parse("2026-10-27T11:00:00.987654321Z");

    private final UserAccount student = ApprovalFixtures.account(70, "student1", RoleName.STUDENT);
    private final UserAccount faculty = ApprovalFixtures.account(80, "faculty1", RoleName.FACULTY);
    private final UserAccount admin = ApprovalFixtures.account(1, "admin1", RoleName.ADMIN);

    private ApprovalRequest threeSteps() {
        return ApprovalFixtures.request(12, ApprovalFixtures.workflow(4, "DEMO",
                RoleName.FACULTY, RoleName.ADMIN, RoleName.ADMIN), student);
    }

    private static List<ApprovalRequestStepStatus> statuses(ApprovalRequest request) {
        return request.getSteps().stream().map(ApprovalRequestStep::getStatus).toList();
    }

    @Test
    void startingCopiesTheStepsWithTheFirstPending() {
        ApprovalRequest request = threeSteps();

        assertThat(request.getStatus()).isEqualTo(ApprovalRequestStatus.PENDING);
        assertThat(request.getCurrentStepOrder()).isEqualTo(1);
        assertThat(request.getCompletedAt()).isNull();
        assertThat(request.getSubmittedAt()).isEqualTo(Instant.parse("2026-10-26T09:30:00.123456Z"));
        assertThat(statuses(request)).containsExactly(PENDING, WAITING, WAITING);
        assertThat(request.getSteps()).extracting(ApprovalRequestStep::getRequiredRoleCode)
                .containsExactly("FACULTY", "ADMIN", "ADMIN");
        assertThat(request.getSteps()).extracting(ApprovalRequestStep::getStepName)
                .containsExactly("Step 1", "Step 2", "Step 3");
        assertThat(request.currentStep()).get().extracting(ApprovalRequestStep::getStepOrder).isEqualTo(1);
    }

    @Test
    void theStepsAreASnapshotThatLaterWorkflowEditsNeverChange() {
        ApprovalWorkflowDefinition workflow = ApprovalFixtures.workflow(4, "DEMO", RoleName.FACULTY);
        ApprovalRequest before = ApprovalFixtures.request(12, workflow, student);

        workflow.update("Renamed", null, true, List.of(new StepSpec("Admin review", ApprovalFixtures.role(RoleName.ADMIN)),
                new StepSpec("Second look", ApprovalFixtures.role(RoleName.ADMIN))));
        ApprovalRequest after = ApprovalFixtures.request(13, workflow, student);

        assertThat(before.getSteps()).singleElement().satisfies(step -> {
            assertThat(step.getRequiredRoleCode()).isEqualTo("FACULTY");
            assertThat(step.getStepName()).isEqualTo("Step 1");
        });
        assertThat(after.getSteps()).extracting(ApprovalRequestStep::getRequiredRoleCode).containsExactly("ADMIN", "ADMIN");
        assertThat(after.getSteps()).extracting(ApprovalRequestStep::getStepName).containsExactly("Admin review", "Second look");
    }

    @Test
    void approvingAStepActivatesTheNextOne() {
        ApprovalRequest request = threeSteps();
        ApprovalRequestStep first = request.currentStep().orElseThrow();

        assertThat(request.approve(first, faculty, "  Looks fine. ", LATER)).get()
                .extracting(ApprovalRequestStep::getStepOrder).isEqualTo(2);

        assertThat(statuses(request)).containsExactly(APPROVED, PENDING, WAITING);
        assertThat(request.getCurrentStepOrder()).isEqualTo(2);
        assertThat(request.getStatus()).isEqualTo(ApprovalRequestStatus.PENDING);
        assertThat(first.getActedBy()).isSameAs(faculty);
        assertThat(first.getActedAt()).isEqualTo(Instant.parse("2026-10-27T11:00:00.987654Z"));
        assertThat(first.getComment()).isEqualTo("Looks fine.");
    }

    @Test
    void approvingTheLastStepApprovesTheRequest() {
        ApprovalRequest request = ApprovalFixtures.request(12, ApprovalFixtures.workflow(4, "DEMO", RoleName.FACULTY,
                RoleName.ADMIN), student);
        request.approve(request.currentStep().orElseThrow(), faculty, null, LATER);

        assertThat(request.approve(request.currentStep().orElseThrow(), admin, null, LATER)).isEmpty();

        assertThat(request.getStatus()).isEqualTo(ApprovalRequestStatus.APPROVED);
        assertThat(request.getCurrentStepOrder()).isNull();
        assertThat(request.getCompletedAt()).isEqualTo(Instant.parse("2026-10-27T11:00:00.987654Z"));
        assertThat(statuses(request)).containsExactly(APPROVED, APPROVED);
        assertThat(request.currentStep()).isEmpty();
    }

    @Test
    void rejectingAStepRejectsTheRequestAndSkipsLaterSteps() {
        ApprovalRequest request = threeSteps();
        request.approve(request.currentStep().orElseThrow(), faculty, null, LATER);

        request.reject(request.currentStep().orElseThrow(), admin, "Fees are not paid.", LATER);

        assertThat(statuses(request)).containsExactly(APPROVED, REJECTED, SKIPPED);
        assertThat(request.getStatus()).isEqualTo(ApprovalRequestStatus.REJECTED);
        assertThat(request.getCurrentStepOrder()).isNull();
        assertThat(request.getCompletedAt()).isNotNull();
        assertThat(request.getSteps().get(1).getComment()).isEqualTo("Fees are not paid.");
        assertThat(request.getSteps().get(2).getActedBy()).isNull();
    }

    @Test
    void cancellingSkipsTheCurrentAndWaitingStepsAndKeepsDecidedOnes() {
        ApprovalRequest request = threeSteps();
        request.approve(request.currentStep().orElseThrow(), faculty, null, LATER);

        request.cancel(LATER);

        assertThat(statuses(request)).containsExactly(APPROVED, SKIPPED, SKIPPED);
        assertThat(request.getStatus()).isEqualTo(ApprovalRequestStatus.CANCELLED);
        assertThat(request.getCurrentStepOrder()).isNull();
        assertThat(request.getCompletedAt()).isNotNull();
    }

    @Test
    void aFinishedRequestRefusesEveryChange() {
        ApprovalRequest request = threeSteps();
        ApprovalRequestStep first = request.currentStep().orElseThrow();
        request.reject(first, faculty, "No.", LATER);
        ApprovalRequestStep second = request.getSteps().get(1);

        assertThatThrownBy(() -> request.approve(first, faculty, null, LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.approve(second, admin, null, LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.reject(second, admin, "No.", LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.cancel(LATER)).isInstanceOf(IllegalStateException.class);
        assertThat(statuses(request)).containsExactly(REJECTED, SKIPPED, SKIPPED);
    }

    @Test
    void onlyTheCurrentStepCanBeDecided() {
        ApprovalRequest request = threeSteps();
        ApprovalRequestStep waiting = request.getSteps().get(1);

        assertThatThrownBy(() -> request.approve(waiting, admin, null, LATER)).isInstanceOf(IllegalStateException.class);
        assertThat(waiting.getStatus()).isEqualTo(WAITING);
    }

    @Test
    void theRequestNeedsItsSourceSubjectAndSteps() {
        ApprovalWorkflowDefinition workflow = ApprovalFixtures.workflow(4, "DEMO", RoleName.FACULTY);
        assertThatThrownBy(() -> new ApprovalRequest(workflow, student, " ", "1", "Subject", LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovalRequest(workflow, student, "TYPE", "x".repeat(101), "Subject", LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovalRequest(workflow, student, "TYPE", "1", "x".repeat(201), LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
