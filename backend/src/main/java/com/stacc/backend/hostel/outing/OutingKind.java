package com.stacc.backend.hostel.outing;

import java.time.Duration;

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
