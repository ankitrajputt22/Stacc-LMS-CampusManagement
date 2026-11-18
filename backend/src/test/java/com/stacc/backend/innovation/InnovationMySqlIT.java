package com.stacc.backend.innovation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InnovationMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private InnovationService innovation;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void anIdeaIsDecidedOrWithdrawnNotBoth() throws Exception {
        Long student = accounts.findByLoginId("cse24002").orElseThrow().getId();
        Long reviewer = accounts.findByLoginId("emp1022").orElseThrow().getId();
        Long ideaId = innovation.submit(student, "Race idea", "A problem.", "A solution.").ideaId();
        innovation.review(reviewer, ideaId, 4, "Worth a try.");
        innovation.review(reviewer, ideaId, 5, "Even better after a second read.");
        assertThat(innovation.forReview(reviewer)).filteredOn(idea -> idea.ideaId().equals(ideaId)).singleElement()
                .satisfies(idea -> assertThat(idea.reviews()).singleElement().extracting(InnovationService.ReviewView::score).isEqualTo(5));

        List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                () -> {
                    innovation.decide(ideaId, InnovationIdea.Status.NOT_SELECTED, "Not this term.");
                    return null;
                },
                () -> innovation.withdraw(student, ideaId));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
    }
}
