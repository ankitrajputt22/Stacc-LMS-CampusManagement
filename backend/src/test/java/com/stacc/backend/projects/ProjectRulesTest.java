package com.stacc.backend.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import org.junit.jupiter.api.Test;

/** A project needs a title, summary, and 1 to 20 members; requests are decided once; mentors answer once. */
class ProjectRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    @Test
    void aProjectNeedsItsDetailsAndAnArchivedOneNoLongerChanges() {
        assertThatThrownBy(() -> new Project(mock(UserAccount.class), "App", "Summary", 21, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Project(mock(UserAccount.class), " ", "Summary", 4, NOW)).isInstanceOf(IllegalArgumentException.class);
        Project project = new Project(mock(UserAccount.class), "Campus app", "A helper for new students.", 4, NOW);
        assertThat(project.getStatus()).isEqualTo(Project.Status.OPEN);
        project.setStatus(Project.Status.ARCHIVED);
        assertThatThrownBy(() -> project.setStatus(Project.Status.OPEN)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requestsAndInvitationsAreAnsweredOnce() {
        Project project = mock(Project.class);
        ProjectMember request = new ProjectMember(project, mock(UserAccount.class), "I build Android apps.", false, NOW);
        request.decide(true, NOW);
        assertThatThrownBy(() -> request.decide(false, NOW)).isInstanceOf(IllegalStateException.class);
        request.withdrawOrLeave(NOW);
        assertThat(request.getStatus()).isEqualTo(ProjectMember.Status.LEFT);

        ProjectMentor mentor = new ProjectMentor(project, mock(UserAccount.class), NOW);
        assertThatThrownBy(() -> mentor.end(NOW)).isInstanceOf(IllegalStateException.class);
        mentor.answer(true, NOW);
        assertThatThrownBy(() -> mentor.answer(false, NOW)).isInstanceOf(IllegalStateException.class);
        mentor.end(NOW);
        assertThat(mentor.getStatus()).isEqualTo(ProjectMentor.Status.ENDED);
    }
}
