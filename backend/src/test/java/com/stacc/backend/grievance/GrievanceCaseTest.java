package com.stacc.backend.grievance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import org.junit.jupiter.api.Test;

/** A grievance moves forward only, may be appealed once within the window, and is overdue only while it waits. */
class GrievanceCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    private final GrievanceCategory category = new GrievanceCategory("ragging", "Ragging", 7, NOW);

    private GrievanceCase submitted() {
        return new GrievanceCase(category, mock(UserAccount.class), "Seniors in hostel B", "What happened, in plain text.", NOW);
    }

    @Test
    void theCodeIsUppercaseAndTheResponseTimeSetsTheRespondByTime() {
        assertThat(category.getCode()).isEqualTo("RAGGING");
        GrievanceCase found = submitted();
        assertThat(found.getRespondBy()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(found.overdue(NOW.plus(Duration.ofDays(8)))).isTrue();
        found.startReview(NOW.plusSeconds(60));
        assertThat(found.overdue(NOW.plus(Duration.ofDays(8)))).isFalse();
    }

    @Test
    void aResolutionIsAppealedOnceWithinTheWindowAndTheDecisionCloses() {
        GrievanceCase found = submitted();
        found.resolve("Warning issued to those involved.", NOW);
        assertThatThrownBy(() -> found.appeal("Not enough.", NOW.plus(Duration.ofDays(15)))).isInstanceOf(IllegalStateException.class);
        found.appeal("Not enough.", NOW.plus(Duration.ofDays(2)));
        assertThat(found.getStatus()).isEqualTo(GrievanceCase.Status.APPEALED);
        assertThatThrownBy(() -> found.accept(NOW)).isInstanceOf(IllegalStateException.class);
        found.decideAppeal("Hostel moved; case closed.", NOW.plus(Duration.ofDays(3)));
        assertThat(found.getStatus()).isEqualTo(GrievanceCase.Status.CLOSED);
        assertThatThrownBy(() -> found.appeal("Again.", NOW.plus(Duration.ofDays(3)))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anInactiveCategoryTakesNoNewCases() {
        category.change("Ragging", 7, false);
        assertThatThrownBy(this::submitted).isInstanceOf(IllegalStateException.class);
    }
}
