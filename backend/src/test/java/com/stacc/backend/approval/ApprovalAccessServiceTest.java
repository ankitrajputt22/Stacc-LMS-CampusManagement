package com.stacc.backend.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import org.junit.jupiter.api.Test;

class ApprovalAccessServiceTest {

    private final ApprovalAccessService access = new ApprovalAccessService();
    private final ApprovalRequest request = ApprovalFixtures.request(12,
            ApprovalFixtures.workflow(4, "TWO_STEP", RoleName.FACULTY, RoleName.ADMIN),
            ApprovalFixtures.account(70, "student1", RoleName.STUDENT));
    private final ApprovalRequestStep facultyStep = request.getSteps().get(0);
    private final ApprovalRequestStep adminStep = request.getSteps().get(1);

    @Test
    void onlyTheRequesterWithViewOwnSeesARequest() {
        assertThat(access.canView(ApprovalFixtures.actor(70, ApprovalActor.REQUEST_VIEW_OWN), request)).isTrue();
        assertThat(access.canView(ApprovalFixtures.actor(70), request)).isFalse();
        assertThat(access.canView(ApprovalFixtures.actor(1, "ROLE_ADMIN", ApprovalActor.REQUEST_VIEW_OWN,
                ApprovalActor.WORKFLOW_MANAGE), request)).isFalse();
    }

    @Test
    void theRoleMustMatchExactlyWithNoAdminPass() {
        UserAccount admin = ApprovalFixtures.account(1, "admin1", RoleName.ADMIN);
        UserAccount both = ApprovalFixtures.account(2, "facultyadmin1", RoleName.FACULTY, RoleName.ADMIN);

        assertThatThrownBy(() -> access.requireDecider(ApprovalFixtures.approver(1, RoleName.ADMIN), admin, request,
                facultyStep)).hasMessage("This step must be decided by an account holding the FACULTY role.");
        assertThatCode(() -> access.requireDecider(ApprovalFixtures.approver(1, RoleName.ADMIN), admin, request, adminStep))
                .doesNotThrowAnyException();
        assertThatCode(() -> access.requireDecider(ApprovalFixtures.approver(2, RoleName.ADMIN, RoleName.FACULTY), both,
                request, facultyStep)).doesNotThrowAnyException();
    }

    @Test
    void theRolesInTheTokenHaveNoMainOne() {
        ApprovalActor actor = ApprovalFixtures.approver(2, RoleName.ADMIN, RoleName.FACULTY);
        assertThat(access.holdsRole(actor, "FACULTY")).isTrue();
        assertThat(access.holdsRole(actor, "ADMIN")).isTrue();
        assertThat(access.holdsRole(actor, "STUDENT")).isFalse();
        assertThat(actor.roleCodes()).containsExactlyInAnyOrder("FACULTY", "ADMIN");
    }
}
