package com.stacc.backend.campus.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.campus.CampusFixtures;
import com.stacc.backend.campus.services.CampusServiceInputs.CreateCampusServiceInput;
import com.stacc.backend.campus.services.CampusServiceInputs.UpdateCampusServiceInput;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class CampusServiceDefinitionServiceTest {

    private final CampusServiceDefinitionRepository services = mock(CampusServiceDefinitionRepository.class);
    private final ApprovalQueryService approvals = mock(ApprovalQueryService.class);
    private final CampusServiceDefinitionService service = new CampusServiceDefinitionService(services, approvals);

    @BeforeEach
    void setUp() {
        when(services.saveAndFlush(any())).thenAnswer(call -> {
            CampusServiceDefinition saved = call.getArgument(0);
            return saved.getId() == null ? CampusFixtures.withId(saved, 7) : saved;
        });
        when(approvals.isStartableWorkflow("CAMPUS_SERVICE_REQUEST")).thenReturn(true);
    }

    private static void assertRefused(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            if (message != null) {
                assertThat(refusal.getMessage()).isEqualTo(message);
            }
        });
    }

    private static CreateCampusServiceInput create(String code, Boolean approval, String workflow) {
        return new CreateCampusServiceInput(code, "ID card replacement", "Lost or damaged cards.", null, true, false,
                approval, workflow);
    }

    @Test
    void createsAnActiveServiceWithAnUppercaseCode() {
        var created = service.create(create("id_card", false, "IGNORED"));

        assertThat(created.serviceId()).isEqualTo(7L);
        assertThat(created.code()).isEqualTo("ID_CARD");
        assertThat(created.active()).isTrue();
        assertThat(created.availableToStudent()).isTrue();
        assertThat(created.availableToFaculty()).isFalse();
        assertThat(created.approvalRequired()).isFalse();
        assertThat(created.approvalWorkflowCode()).isNull();
        verify(approvals, never()).isStartableWorkflow(any());
    }

    @Test
    void aServiceThatNeedsApprovalNamesAnActiveWorkflow() {
        assertThat(service.create(create("ID_CARD", true, "campus_service_request")).approvalWorkflowCode())
                .isEqualTo("CAMPUS_SERVICE_REQUEST");

        assertRefused(() -> service.create(create("TRANSPORT", true, "UNKNOWN")), HttpStatus.CONFLICT,
                CampusServiceDefinitionService.WORKFLOW_UNAVAILABLE);
        assertRefused(() -> service.create(create("TRANSPORT", true, null)), HttpStatus.BAD_REQUEST, null);
    }

    @Test
    void codesAreUniqueAlsoWhenTheDatabaseCatchesARace() {
        when(services.existsByCode("ID_CARD")).thenReturn(true);
        assertRefused(() -> service.create(create("id_card", false, null)), HttpStatus.CONFLICT,
                CampusServiceDefinitionService.DUPLICATE);

        doThrow(new DataIntegrityViolationException("uk_campus_service_definitions_code")).when(services).saveAndFlush(any());
        assertRefused(() -> service.create(create("TRANSPORT", false, null)), HttpStatus.CONFLICT,
                CampusServiceDefinitionService.DUPLICATE);
    }

    @Test
    void invalidCodesAndNoAudienceAre400() {
        assertRefused(() -> service.create(create("ID-CARD", false, null)), HttpStatus.BAD_REQUEST, null);
        assertRefused(() -> service.create(new CreateCampusServiceInput("X", "X", null, true, false, false, false, null)),
                HttpStatus.BAD_REQUEST, null);
    }

    @Test
    void updatesEverythingButTheCode() {
        CampusServiceDefinition existing = CampusFixtures.service(7, "ID_CARD", true, false, null);
        when(services.findById(7L)).thenReturn(Optional.of(existing));

        var updated = service.update(7L, new UpdateCampusServiceInput("New name", null, false, true, true, true,
                "CAMPUS_SERVICE_REQUEST"));

        assertThat(updated.code()).isEqualTo("ID_CARD");
        assertThat(updated.name()).isEqualTo("New name");
        assertThat(updated.active()).isFalse();
        assertThat(updated.availableToFaculty()).isTrue();
        assertThat(updated.approvalWorkflowCode()).isEqualTo("CAMPUS_SERVICE_REQUEST");
        assertThat(UpdateCampusServiceInput.class.getRecordComponents()).noneMatch(part -> part.getName().equals("code"));
    }

    @Test
    void theWorkflowIsCheckedWhenTheApprovalSettingChangesOnly() {
        CampusServiceDefinition existing = CampusFixtures.service(7, "ID_CARD", true, false, "OLD_FLOW");
        when(services.findById(7L)).thenReturn(Optional.of(existing));

        // Unchanged, so a since-deactivated workflow does not block editing the name.
        service.update(7L, new UpdateCampusServiceInput("Renamed", null, true, true, false, true, "OLD_FLOW"));
        verify(approvals, never()).isStartableWorkflow("OLD_FLOW");

        assertRefused(() -> service.update(7L, new UpdateCampusServiceInput("Renamed", null, true, true, false, true,
                "OTHER_FLOW")), HttpStatus.CONFLICT, CampusServiceDefinitionService.WORKFLOW_UNAVAILABLE);
    }

    @Test
    void anUnknownServiceIs404() {
        assertRefused(() -> service.get(99L), HttpStatus.NOT_FOUND, CampusServiceDefinitionService.NOT_FOUND);
        assertRefused(() -> service.update(null, new UpdateCampusServiceInput("X", null, true, true, false, false, null)),
                HttpStatus.NOT_FOUND, CampusServiceDefinitionService.NOT_FOUND);
    }

    @Test
    void availableServicesMatchEveryRoleTheAccountHolds() {
        CampusServiceDefinition both = CampusFixtures.service(1, "GENERAL_ADMIN", true, true, null);
        when(services.findAvailable(true, false)).thenReturn(List.of(both));
        when(services.findAvailable(false, true)).thenReturn(List.of(both));
        when(services.findAvailable(true, true)).thenReturn(List.of(both));

        assertThat(service.available(CampusFixtures.actor(70, "ROLE_STUDENT"))).hasSize(1);
        verify(services).findAvailable(true, false);
        assertThat(service.available(CampusFixtures.actor(80, "ROLE_FACULTY"))).hasSize(1);
        verify(services).findAvailable(false, true);
        assertThat(service.available(CampusFixtures.actor(90, "ROLE_STUDENT", "ROLE_FACULTY")))
                .singleElement().satisfies(item -> {
                    assertThat(item.code()).isEqualTo("GENERAL_ADMIN");
                    assertThat(item.approvalRequired()).isFalse();
                });
        verify(services).findAvailable(true, true);
    }

    @Test
    void anAccountWithoutARequesterRoleSeesNoServices() {
        assertThat(service.available(CampusFixtures.actor(1, "ROLE_ADMIN"))).isEmpty();
        verify(services, never()).findAvailable(anyBoolean(), anyBoolean());
    }

    @Test
    void theRequesterViewHidesTheWorkflow() {
        assertThat(CampusServiceResponses.CampusServiceSummary.class.getRecordComponents())
                .noneMatch(part -> part.getName().toLowerCase().contains("workflow"));
    }
}
