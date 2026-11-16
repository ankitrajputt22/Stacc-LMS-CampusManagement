package com.stacc.backend.hostel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** A swap waits for its partner's one answer; any change ends once, and only an open one ends. */
class HostelRoomChangeTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    @Test
    void aSwapIsAnsweredOnceAndDecliningEndsIt() {
        HostelRoomChange swap = HostelRoomChange.swap(mock(HostelAllocation.class), mock(HostelAllocation.class), "Closer to my friends", NOW);
        assertThat(swap.getPartnerResponse()).isEqualTo(HostelRoomChange.PartnerResponse.PENDING);
        swap.answer(false);
        assertThat(swap.getStatus()).isEqualTo(HostelRoomChange.Status.DECLINED);
        assertThatThrownBy(() -> swap.answer(true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(swap::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMoveHasNoPartnerAndEndsOnce() {
        HostelRoomChange move = HostelRoomChange.move(mock(HostelAllocation.class), "Too noisy", "A quiet floor", NOW);
        assertThat(move.getPartnerResponse()).isNull();
        assertThatThrownBy(() -> move.answer(true)).isInstanceOf(IllegalStateException.class);
        move.linkApproval(9L);
        assertThatThrownBy(() -> move.linkApproval(10L)).isInstanceOf(IllegalStateException.class);
        move.cancel();
        assertThatThrownBy(() -> move.complete(null, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> HostelRoomChange.move(mock(HostelAllocation.class), " ", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
