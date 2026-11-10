package com.stacc.backend.grievance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Only the complainant and the category's committee open a case, admins included; resolving and accepting at once never
 * both succeed past the case row lock. A category of its own.
 */
class GrievanceAccessMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private GrievanceService grievances;

    @Autowired
    private GrievanceAdminService admin;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void aCaseIsOpenedOnlyByItsComplainantAndCommittee() throws Exception {
        Long adminId = accounts.findByLoginId("admin1").orElseThrow().getId();
        Long student = accounts.findByLoginId("student1").orElseThrow().getId();
        Long member = accounts.findByLoginId("faculty1").orElseThrow().getId();
        Long outsider = accounts.findByLoginId("faculty2").orElseThrow().getId();
        Long categoryId = admin.create("RACE_GRIEVANCE", "Race grievance", 7).categoryId();
        admin.addMember(adminId, categoryId, "faculty1");

        Long caseId = grievances.submit(student, categoryId, "Subject", "Details").caseId();

        assertThat(grievances.committeeCase(member, caseId).complainantLoginId()).isEqualTo("student1");
        assertThatThrownBy(() -> grievances.committeeCase(outsider, caseId)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> grievances.committeeCase(adminId, caseId)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> grievances.myCase(outsider, caseId)).isInstanceOf(ApiException.class);
        assertThat(grievances.myCase(student, caseId).complainantLoginId()).isNull();

        grievances.resolve(member, caseId, "Resolved.");
        List<Concurrently.Outcome<GrievanceService.CaseView>> outcomes = Concurrently.run(
                () -> grievances.accept(student, caseId),
                () -> grievances.appeal(student, caseId, "Not enough."));
        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
    }
}
