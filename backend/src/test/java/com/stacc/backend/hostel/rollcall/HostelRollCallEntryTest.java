package com.stacc.backend.hostel.rollcall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.hostel.HostelAllocation;
import org.junit.jupiter.api.Test;

/** Nobody starts as absent: residents start unmarked, or out when the gate log says so, and are never unmarked again. */
class HostelRollCallEntryTest {

    private static final Instant NOW = Instant.parse("2026-11-12T16:30:00Z");

    @Test
    void residentsStartUnmarkedOrOutAndAreNeverUnmarkedAgain() {
        HostelRollCall sheet = mock(HostelRollCall.class);
        HostelRollCallEntry inside = new HostelRollCallEntry(sheet, mock(HostelAllocation.class), false);
        HostelRollCallEntry outside = new HostelRollCallEntry(sheet, mock(HostelAllocation.class), true);
        assertThat(inside.getStatus()).isEqualTo(HostelRollCallEntry.Status.UNMARKED);
        assertThat(outside.getStatus()).isEqualTo(HostelRollCallEntry.Status.OUT);
        assertThat(outside.isPrefilledOut()).isTrue();

        inside.mark(HostelRollCallEntry.Status.PRESENT, mock(UserAccount.class), NOW);
        assertThat(inside.getStatus()).isEqualTo(HostelRollCallEntry.Status.PRESENT);
        assertThatThrownBy(() -> inside.mark(HostelRollCallEntry.Status.UNMARKED, mock(UserAccount.class), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
