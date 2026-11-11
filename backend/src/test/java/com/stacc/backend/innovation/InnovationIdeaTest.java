package com.stacc.backend.innovation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import org.junit.jupiter.api.Test;

/** Decisions move forward only, a decided idea is no longer withdrawn, and scores are 1 to 5. */
class InnovationIdeaTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    private InnovationIdea idea() {
        return new InnovationIdea(mock(UserAccount.class), "Solar benches", "No charging spots outdoors.", "Benches with solar USB ports.", NOW);
    }

    @Test
    void decisionsMoveForwardOnly() {
        InnovationIdea idea = idea();
        assertThatThrownBy(() -> idea.decide(InnovationIdea.Status.SUBMITTED, null, NOW)).isInstanceOf(IllegalStateException.class);
        idea.decide(InnovationIdea.Status.SHORTLISTED, "Promising.", NOW);
        idea.decide(InnovationIdea.Status.ACCEPTED, "Funded for a pilot.", NOW);
        assertThat(idea.decided()).isTrue();
        assertThatThrownBy(() -> idea.decide(InnovationIdea.Status.NOT_SELECTED, null, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(idea::withdraw).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void scoresAreOneToFive() {
        InnovationReview review = new InnovationReview(idea(), mock(UserAccount.class));
        assertThatThrownBy(() -> review.set(6, "Great", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> review.set(3, " ", NOW)).isInstanceOf(IllegalArgumentException.class);
        review.set(4, "Clear and cheap.", NOW);
        assertThat(review.getScore()).isEqualTo(4);
    }
}
