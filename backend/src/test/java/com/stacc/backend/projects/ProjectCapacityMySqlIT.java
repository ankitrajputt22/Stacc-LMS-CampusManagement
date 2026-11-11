package com.stacc.backend.projects;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Accepting two join requests at once for the last place accepts exactly one. A project of its own. */
class ProjectCapacityMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private ProjectService projects;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void aProjectNeverPassesItsMemberLimit() throws Exception {
        Long lead = accounts.findByLoginId("student1").orElseThrow().getId();
        Long first = accounts.findByLoginId("student2").orElseThrow().getId();
        Long second = accounts.findByLoginId("student3").map(account -> account.getId())
                .orElseGet(() -> accounts.findByLoginId("faculty1").orElseThrow().getId());
        Long projectId = projects.create(lead, "Race project", "Testing the member limit.", 2).projectId();
        projects.requestToJoin(first, projectId, null);
        projects.requestToJoin(second, projectId, null);
        List<Long> requests = projects.list(lead).stream().filter(view -> view.projectId().equals(projectId)).findFirst().orElseThrow()
                .members().stream().filter(member -> !member.lead()).map(ProjectService.MemberView::memberId).toList();

        List<Concurrently.Outcome<ProjectService.ProjectView>> outcomes = Concurrently.run(
                () -> projects.decide(lead, requests.get(0), true),
                () -> projects.decide(lead, requests.get(1), true));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(projects.list(lead).stream().filter(view -> view.projectId().equals(projectId)).findFirst().orElseThrow().activeMembers())
                .isEqualTo(2);
    }
}
