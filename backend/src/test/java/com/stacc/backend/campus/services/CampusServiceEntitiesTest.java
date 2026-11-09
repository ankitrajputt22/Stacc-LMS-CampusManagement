package com.stacc.backend.campus.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import com.stacc.backend.approval.ApprovalRequest;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.campus.CampusFixtures;
import org.junit.jupiter.api.Test;

class CampusServiceEntitiesTest {

    private final UserAccount student = CampusFixtures.account(70, "student1", RoleName.STUDENT);
    private final UserAccount admin = CampusFixtures.account(1, "admin1", RoleName.ADMIN);

    @Test
    void aServiceCodeIsUppercaseCheckedAndHasNoSetter() {
        CampusServiceDefinition service = new CampusServiceDefinition(" id_card ",
                CampusFixtures.settings(true, false, null));

        assertThat(service.getCode()).isEqualTo("ID_CARD");
        assertThat(service.isActive()).isTrue();
        for (String bad : new String[] {"", "1CARD", "ID-CARD", "ID CARD", "X".repeat(81)}) {
            assertThatThrownBy(() -> new CampusServiceDefinition(bad, CampusFixtures.settings(true, false, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(CampusServiceDefinition.class.getMethods()).noneMatch(method -> method.getName().equals("setCode"));
    }

    @Test
    void aServiceIsOfferedToAtLeastOneRoleAndMatchesAnyRoleTheAccountHolds() {
        assertThatThrownBy(() -> new CampusServiceDefinition("NONE", CampusFixtures.settings(false, false, null)))
                .isInstanceOf(IllegalArgumentException.class);
        CampusServiceDefinition studentOnly = CampusFixtures.service(1, "ID_CARD", true, false, null);
        CampusServiceDefinition facultyOnly = CampusFixtures.service(2, "LAB_KEY", false, true, null);

        assertThat(studentOnly.isAvailableTo(Set.of("STUDENT"))).isTrue();
        assertThat(studentOnly.isAvailableTo(Set.of("FACULTY"))).isFalse();
        assertThat(studentOnly.isAvailableTo(Set.of("ADMIN"))).isFalse();
        assertThat(facultyOnly.isAvailableTo(Set.of("FACULTY"))).isTrue();
        assertThat(facultyOnly.isAvailableTo(Set.of("STUDENT", "FACULTY"))).isTrue();
    }

    @Test
    void approvalNeedsAWorkflowCodeAndNoApprovalDropsIt() {
        assertThatThrownBy(() -> new CampusServiceDefinition("X", new CampusServiceDefinition.Settings("X", null, true,
                true, false, true, " "))).isInstanceOf(IllegalArgumentException.class);
        CampusServiceDefinition service = new CampusServiceDefinition("X", new CampusServiceDefinition.Settings("X", null,
                true, true, false, false, "CAMPUS_SERVICE_REQUEST"));
        assertThat(service.getApprovalWorkflowCode()).isNull();

        service.update(new CampusServiceDefinition.Settings("X", null, false, true, true, true, "campus_service_request"));
        assertThat(service.getApprovalWorkflowCode()).isEqualTo("CAMPUS_SERVICE_REQUEST");
        assertThat(service.isActive()).isFalse();
        assertThat(service.getCode()).isEqualTo("X");
    }

    @Test
    void aRequestStartsSubmittedWithApprovalAndApprovedWithout() {
        CampusServiceRequest withApproval = CampusFixtures.request(42,
                CampusFixtures.service(1, "ID_CARD", true, false, "CAMPUS_SERVICE_REQUEST"), student,
                CampusFixtures.approval(12, student));
        CampusServiceRequest without = CampusFixtures.request(43, CampusFixtures.service(2, "GENERAL", true, true, null),
                student, null);

        assertThat(withApproval.getStatus()).isEqualTo(CampusServiceRequestStatus.SUBMITTED);
        assertThat(withApproval.getRequestNumber()).isEqualTo("CSR-000042");
        assertThat(withApproval.approvalStatus()).isEqualTo(ApprovalRequestStatus.PENDING);
        assertThat(without.getStatus()).isEqualTo(CampusServiceRequestStatus.APPROVED);
        assertThat(without.approvalStatus()).isNull();
        assertThat(CampusServiceRequest.numberFor(1_234_567)).isEqualTo("CSR-1234567");
    }

    @Test
    void subjectAndDetailsArePlainTextWithinTheirLimits() {
        CampusServiceDefinition service = CampusFixtures.service(2, "GENERAL", true, true, null);
        assertThatThrownBy(() -> new CampusServiceRequest(service, student, " ", "Details", CampusFixtures.NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampusServiceRequest(service, student, "x".repeat(201), "Details", CampusFixtures.NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampusServiceRequest(service, student, "Subject", "x".repeat(10_001), CampusFixtures.NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new CampusServiceRequest(service, student, "Subject", "<b>x</b>", CampusFixtures.NOW).getDetails())
                .isEqualTo("<b>x</b>");
    }

    @Test
    void theEffectiveStatusFollowsTheApprovalOnlyWhileSubmitted() {
        CampusServiceDefinition service = CampusFixtures.service(1, "ID_CARD", true, false, "CAMPUS_SERVICE_REQUEST");
        CampusServiceRequest approved = CampusFixtures.request(42, service, student, CampusFixtures.approved(12, student, admin));
        CampusServiceRequest rejected = CampusFixtures.request(43, service, student,
                CampusFixtures.rejected(13, student, admin, "Not eligible."));
        ApprovalRequest cancelledApproval = CampusFixtures.approval(14, student);
        cancelledApproval.cancel(CampusFixtures.NOW);
        CampusServiceRequest cancelled = CampusFixtures.request(44, service, student, cancelledApproval);
        CampusServiceRequest pending = CampusFixtures.request(45, service, student, CampusFixtures.approval(15, student));

        assertThat(approved.effectiveStatus()).isEqualTo(CampusServiceRequestStatus.APPROVED);
        assertThat(rejected.effectiveStatus()).isEqualTo(CampusServiceRequestStatus.REJECTED);
        assertThat(cancelled.effectiveStatus()).isEqualTo(CampusServiceRequestStatus.CANCELLED);
        assertThat(pending.effectiveStatus()).isEqualTo(CampusServiceRequestStatus.SUBMITTED);
        // Reading never stores anything.
        assertThat(approved.getStatus()).isEqualTo(CampusServiceRequestStatus.SUBMITTED);

        assertThat(approved.synchronizeWithApproval()).isTrue();
        assertThat(approved.getStatus()).isEqualTo(CampusServiceRequestStatus.APPROVED);
        assertThat(approved.synchronizeWithApproval()).isFalse();
        assertThat(pending.synchronizeWithApproval()).isFalse();
        // A completed request stays completed whatever its approval says.
        approved.complete(CampusFixtures.NOW);
        assertThat(approved.effectiveStatus()).isEqualTo(CampusServiceRequestStatus.COMPLETED);
    }

    @Test
    void theStatusFilterMapsToTheApprovalOutcomeThatLeadsToIt() {
        assertThat(CampusServiceRequestStatus.APPROVED.approvalEquivalent()).isEqualTo(ApprovalRequestStatus.APPROVED);
        assertThat(CampusServiceRequestStatus.REJECTED.approvalEquivalent()).isEqualTo(ApprovalRequestStatus.REJECTED);
        assertThat(CampusServiceRequestStatus.CANCELLED.approvalEquivalent()).isEqualTo(ApprovalRequestStatus.CANCELLED);
        assertThat(CampusServiceRequestStatus.SUBMITTED.approvalEquivalent()).isNull();
        assertThat(CampusServiceRequestStatus.COMPLETED.approvalEquivalent()).isNull();
    }

    @Test
    void onlyAnApprovedRequestCompletesAndOnlyOpenOnesCancel() {
        CampusServiceDefinition service = CampusFixtures.service(2, "GENERAL", true, true, null);
        CampusServiceRequest request = CampusFixtures.request(43, service, student, null);

        request.complete(CampusFixtures.NOW);
        assertThat(request.getStatus()).isEqualTo(CampusServiceRequestStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isEqualTo(CampusFixtures.NOW.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThatThrownBy(() -> request.complete(CampusFixtures.NOW.plusSeconds(5))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(request::cancel).isInstanceOf(IllegalStateException.class);

        CampusServiceRequest open = CampusFixtures.request(44, service, student, null);
        open.cancel();
        assertThat(open.getStatus()).isEqualTo(CampusServiceRequestStatus.CANCELLED);
        assertThatThrownBy(open::cancel).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> open.complete(CampusFixtures.NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(open.getCompletedAt()).isNull();
    }

    @Test
    void anApprovalIsLinkedOnce() {
        CampusServiceRequest request = CampusFixtures.request(42,
                CampusFixtures.service(1, "ID_CARD", true, false, "CAMPUS_SERVICE_REQUEST"), student,
                CampusFixtures.approval(12, student));
        assertThatThrownBy(() -> request.linkApproval(CampusFixtures.approval(13, student)))
                .isInstanceOf(IllegalStateException.class);
    }
}
