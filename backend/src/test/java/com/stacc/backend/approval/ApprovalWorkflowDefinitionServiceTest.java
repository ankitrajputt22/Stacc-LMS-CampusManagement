package com.stacc.backend.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalApiRequests.ApprovalWorkflowStepRequest;
import com.stacc.backend.approval.ApprovalApiRequests.CreateApprovalWorkflowRequest;
import com.stacc.backend.approval.ApprovalApiRequests.UpdateApprovalWorkflowRequest;
import com.stacc.backend.approval.ApprovalResponses.ApprovalWorkflowDetail;
import com.stacc.backend.approval.ApprovalResponses.ApprovalWorkflowStepResponse;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.role.RoleRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class ApprovalWorkflowDefinitionServiceTest {

    private final ApprovalWorkflowDefinitionRepository workflows = mock(ApprovalWorkflowDefinitionRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final ApprovalWorkflowDefinitionService service = new ApprovalWorkflowDefinitionService(workflows, roles);

    @BeforeEach
    void setUp() {
        for (RoleName name : RoleName.values()) {
            when(roles.findByName(name)).thenReturn(Optional.of(ApprovalFixtures.role(name)));
        }
        when(workflows.saveAndFlush(any())).thenAnswer(call -> {
            ApprovalWorkflowDefinition saved = call.getArgument(0);
            return saved.getId() == null ? ApprovalFixtures.withId(saved, 9) : saved;
        });
    }

    private static void assertRefused(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, refusal -> assertThat(refusal.getStatus()).isEqualTo(status));
    }

    private static ApprovalWorkflowStepRequest step(String name, String role) {
        return new ApprovalWorkflowStepRequest(name, role);
    }

    private static CreateApprovalWorkflowRequest create(String code, List<ApprovalWorkflowStepRequest> steps) {
        return new CreateApprovalWorkflowRequest(code, "Hostel application", "Two reviews.", steps);
    }

    @Test
    void aValidWorkflowIsCreatedActiveWithNumberedSteps() {
        ApprovalWorkflowDetail created = service.create(create("hostel_application",
                List.of(step("Faculty review", "faculty"), step("Office approval", " ADMIN "))));

        assertThat(created.workflowId()).isEqualTo(9);
        assertThat(created.code()).isEqualTo("HOSTEL_APPLICATION");
        assertThat(created.active()).isTrue();
        assertThat(created.steps()).extracting(ApprovalWorkflowStepResponse::stepOrder,
                ApprovalWorkflowStepResponse::approverRoleCode)
                .containsExactly(tuple(1, "FACULTY"),
                        tuple(2, "ADMIN"));
    }

    @Test
    void aTakenCodeIsAConflictAlsoInARace() {
        when(workflows.existsByCode("DEMO")).thenReturn(true);
        assertRefused(() -> service.create(create("demo", List.of(step("A", "ADMIN")))), HttpStatus.CONFLICT);

        when(workflows.existsByCode("OTHER")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("uk_approval_workflow_definitions_code")).when(workflows).saveAndFlush(any());
        assertThatThrownBy(() -> service.create(create("other", List.of(step("A", "ADMIN")))))
                .hasMessage(ApprovalWorkflowDefinitionService.CODE_TAKEN);
    }

    @Test
    void stepsAndRolesAreChecked() {
        assertRefused(() -> service.create(create("DEMO", List.of())), HttpStatus.BAD_REQUEST);
        assertRefused(() -> service.create(create("DEMO", Collections.nCopies(11, step("A", "ADMIN")))), HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.create(create("DEMO", List.of(step("A", "DEAN")))))
                .hasMessage("Unknown approver role: DEAN.");
        assertThatThrownBy(() -> service.create(create("DEMO", List.of(step("A", "STUDENT")))))
                .hasMessage("The STUDENT role cannot decide approval steps.");
        assertRefused(() -> service.create(create("1 DEMO", List.of(step("A", "ADMIN")))), HttpStatus.BAD_REQUEST);
        verify(workflows, never()).saveAndFlush(any());
    }

    @Test
    void updatingReplacesEverythingButTheCode() {
        ApprovalWorkflowDefinition workflow = ApprovalFixtures.workflow(4, "DEMO", RoleName.FACULTY, RoleName.ADMIN);
        when(workflows.findWithStepsById(4L)).thenReturn(Optional.of(workflow));

        ApprovalWorkflowDetail updated = service.update(4L, new UpdateApprovalWorkflowRequest("Demo two", "Changed.",
                false, List.of(step("Office approval", "ADMIN"))));

        assertThat(updated.code()).isEqualTo("DEMO");
        assertThat(updated.name()).isEqualTo("Demo two");
        assertThat(updated.active()).isFalse();
        assertThat(updated.steps()).singleElement().satisfies(step -> {
            assertThat(step.stepOrder()).isEqualTo(1);
            assertThat(step.approverRoleCode()).isEqualTo("ADMIN");
        });
    }

    @Test
    void updatingAMissingWorkflowIs404() {
        assertRefused(() -> service.update(404L, new UpdateApprovalWorkflowRequest("Demo", null, true,
                List.of(step("A", "ADMIN")))), HttpStatus.NOT_FOUND);
    }
}
