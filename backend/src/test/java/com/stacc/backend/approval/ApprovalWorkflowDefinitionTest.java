package com.stacc.backend.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;

import com.stacc.backend.approval.ApprovalWorkflowDefinition.StepSpec;
import com.stacc.backend.auth.role.RoleName;
import org.junit.jupiter.api.Test;

class ApprovalWorkflowDefinitionTest {

    private static StepSpec step(String name, RoleName role) {
        return new StepSpec(name, ApprovalFixtures.role(role));
    }

    @Test
    void stepsAreNumberedFromOneInTheOrderGiven() {
        ApprovalWorkflowDefinition workflow = new ApprovalWorkflowDefinition(" hostel_application ", "Hostel", null,
                List.of(step("Faculty review", RoleName.FACULTY), step("Office approval", RoleName.ADMIN)));

        assertThat(workflow.getCode()).isEqualTo("HOSTEL_APPLICATION");
        assertThat(workflow.isActive()).isTrue();
        assertThat(workflow.getSteps()).extracting(ApprovalWorkflowStepDefinition::getStepOrder).containsExactly(1, 2);
        assertThat(workflow.getSteps()).extracting(step -> step.getApproverRole().getName())
                .containsExactly(RoleName.FACULTY, RoleName.ADMIN);
    }

    @Test
    void replacingStepsChangesRowsInPlaceAddsAndRemoves() {
        ApprovalWorkflowDefinition workflow = ApprovalFixtures.workflow(4, "DEMO", RoleName.FACULTY, RoleName.ADMIN);
        ApprovalWorkflowStepDefinition first = workflow.getSteps().get(0);

        workflow.update("Demo", "Now three steps.", false, List.of(step("A", RoleName.ADMIN), step("B", RoleName.FACULTY),
                step("C", RoleName.ADMIN)));
        assertThat(workflow.getSteps()).hasSize(3);
        assertThat(workflow.getSteps().get(0)).isSameAs(first);
        assertThat(first.getName()).isEqualTo("A");
        assertThat(first.getApproverRole().getName()).isEqualTo(RoleName.ADMIN);
        assertThat(workflow.isActive()).isFalse();

        workflow.update("Demo", null, true, List.of(step("Only", RoleName.FACULTY)));
        assertThat(workflow.getSteps()).extracting(ApprovalWorkflowStepDefinition::getStepOrder).containsExactly(1);
        assertThat(workflow.getCode()).isEqualTo("DEMO");
    }

    @Test
    void aWorkflowHasOneToTenStepsAndAValidCode() {
        assertThatThrownBy(() -> new ApprovalWorkflowDefinition("DEMO", "Demo", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovalWorkflowDefinition("DEMO", "Demo", null,
                Collections.nCopies(11, step("S", RoleName.ADMIN)))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ApprovalWorkflowDefinition("DEMO", "Demo", null, Collections.nCopies(10, step("S", RoleName.ADMIN)))
                .getSteps()).hasSize(10);
        assertThatThrownBy(() -> ApprovalWorkflowDefinition.normalizedCode("1DEMO")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApprovalWorkflowDefinition.normalizedCode("A".repeat(81))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovalWorkflowDefinition("DEMO", "Demo", null, List.of(step(" ", RoleName.ADMIN))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
