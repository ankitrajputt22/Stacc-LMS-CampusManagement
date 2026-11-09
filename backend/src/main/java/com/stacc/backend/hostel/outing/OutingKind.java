package com.stacc.backend.hostel.outing;

import java.time.Duration;

/** A gate pass is a short outing (at most a day); leave is longer (at most 60 days) and may be extended. */
public enum OutingKind {
    GATE_PASS(Duration.ofHours(24)),
    LEAVE(Duration.ofDays(60));

    private final Duration longest;

    OutingKind(Duration longest) {
        this.longest = longest;
    }

    public Duration longest() {
        return longest;
    }
}
