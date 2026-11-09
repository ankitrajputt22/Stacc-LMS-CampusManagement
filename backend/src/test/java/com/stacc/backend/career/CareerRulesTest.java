package com.stacc.backend.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class CareerRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

    private static CareerOpportunity opportunity() {
        CareerOpportunity opportunity = new CareerOpportunity(1L);
        opportunity.edit(CareerOpportunity.Kind.PLACEMENT, "Graduate engineer", null, "Acme", null, List.of(3L, 3L, 4L),
                NOW.plusSeconds(86_400), List.of("Applied", "Test", "Interview"));
        return opportunity;
    }

    @Test
    void stagesAndProgramsAreKeptInOrderAndOnlyDraftsChange() {
        CareerOpportunity opportunity = opportunity();
        assertThat(opportunity.stageNames()).containsExactly("Applied", "Test", "Interview");
        assertThat(opportunity.programIds()).containsExactly(3L, 4L);
        opportunity.open();
        assertThatThrownBy(() -> opportunity.edit(CareerOpportunity.Kind.PLACEMENT, "x", null, null, null, null, NOW, List.of("A")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void passingTheLastStageSelectsAndAFinishedApplicationNeverMoves() {
        CareerApplication application = new CareerApplication(opportunity(), null, null, null, true, NOW);
        application.advance(3);
        application.advance(3);
        assertThat(application.getStatus()).isEqualTo(CareerApplication.Status.IN_PROGRESS);
        application.advance(3);
        assertThat(application.getStatus()).isEqualTo(CareerApplication.Status.SELECTED);
        assertThatThrownBy(application::reject).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anOfferIsAnsweredOnceAndProfileLinksMustBeHttps() {
        CareerOffer offer = new CareerOffer(new CareerApplication(opportunity(), null, null, null, true, NOW), "Engineer", null, null, NOW);
        offer.respond(true, NOW);
        assertThatThrownBy(() -> offer.respond(false, NOW)).isInstanceOf(IllegalStateException.class);
        CareerProfile profile = new CareerProfile(1L, NOW);
        assertThatThrownBy(() -> profile.edit(null, null, "javascript:alert(1)", NOW)).isInstanceOf(IllegalArgumentException.class);
        profile.edit("Backend developer", "Java\nSQL", "https://github.com/example", NOW);
        assertThat(profile.getSkills()).isEqualTo("Java\nSQL");
    }
}
