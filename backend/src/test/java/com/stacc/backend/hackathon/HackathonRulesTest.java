package com.stacc.backend.hackathon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Work is taken before the end, judging runs after it, and results are released once after the end. */
class HackathonRulesTest {

    private static final Instant START = Instant.parse("2026-12-01T04:00:00Z");
    private static final Instant END = Instant.parse("2026-12-02T04:00:00Z");

    @Test
    void phasesFollowTheClock() {
        Hackathon hackathon = new Hackathon("Code Sprint", null, START, END, 4, START);
        assertThat(hackathon.takingWork(START)).isFalse();
        hackathon.publish();
        assertThat(hackathon.takingWork(START)).isTrue();
        assertThat(hackathon.judging(START)).isFalse();
        assertThatThrownBy(() -> hackathon.releaseResults(START)).isInstanceOf(IllegalStateException.class);
        assertThat(hackathon.takingWork(END)).isFalse();
        assertThat(hackathon.judging(END)).isTrue();
        hackathon.releaseResults(END);
        assertThat(hackathon.judging(END)).isFalse();
        assertThatThrownBy(() -> hackathon.releaseResults(END)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void detailsAreChecked() {
        assertThatThrownBy(() -> new Hackathon("Sprint", null, END, START, 4, START)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Hackathon("Sprint", null, START, END, 11, START)).isInstanceOf(IllegalArgumentException.class);
        HackathonSubmission submission = new HackathonSubmission(null);
        assertThatThrownBy(() -> submission.set("App", "Summary", "ftp://x", START)).isInstanceOf(IllegalArgumentException.class);
        HackathonScore score = new HackathonScore(null, null);
        assertThatThrownBy(() -> score.set(101, null, START)).isInstanceOf(IllegalArgumentException.class);
    }
}
