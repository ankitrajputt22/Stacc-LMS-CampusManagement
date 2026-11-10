package com.stacc.backend.hostel.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.hostel.HostelAllocation;
import org.junit.jupiter.api.Test;

/** A visit moves forward only; a walk-in arrives at once; visiting hours open before they close. */
class HostelVisitTest {

    private static final Instant NOW = Instant.parse("2026-11-12T11:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    @Test
    void aRegisteredVisitArrivesThenLeavesAndOnlyAnExpectedOneIsCancelled() {
        HostelVisit visit = HostelVisit.preRegistered(mock(HostelAllocation.class), "Asha Rao", "Mother", TODAY, NOW);
        assertThat(visit.getStatus()).isEqualTo(HostelVisit.Status.EXPECTED);
        assertThatThrownBy(() -> visit.leave(NOW)).isInstanceOf(IllegalStateException.class);
        visit.arrive(mock(UserAccount.class), NOW);
        assertThatThrownBy(visit::cancel).isInstanceOf(IllegalStateException.class);
        visit.leave(NOW.plusSeconds(3600));
        assertThat(visit.getStatus()).isEqualTo(HostelVisit.Status.LEFT);

        HostelVisit walkIn = HostelVisit.walkIn(mock(HostelAllocation.class), "Ravi Rao", "Brother", TODAY, mock(UserAccount.class), NOW);
        assertThat(walkIn.getStatus()).isEqualTo(HostelVisit.Status.ARRIVED);
        assertThat(walkIn.getKind()).isEqualTo(HostelVisit.Kind.WALK_IN);
    }

    @Test
    void visitingHoursContainTheirOpeningButNotTheirClosing() {
        HostelVisitingHours hours = new HostelVisitingHours(3L, LocalTime.of(16, 0), LocalTime.of(19, 0), NOW);
        assertThat(hours.contains(LocalTime.of(16, 0))).isTrue();
        assertThat(hours.contains(LocalTime.of(18, 59))).isTrue();
        assertThat(hours.contains(LocalTime.of(19, 0))).isFalse();
        assertThatThrownBy(() -> hours.change(LocalTime.of(19, 0), LocalTime.of(16, 0), NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
