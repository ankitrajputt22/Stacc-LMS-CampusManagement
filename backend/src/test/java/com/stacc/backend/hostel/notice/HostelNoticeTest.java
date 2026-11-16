package com.stacc.backend.hostel.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import com.stacc.backend.hostel.Hostel;
import org.junit.jupiter.api.Test;

/** A notice is published once, keeps its emergency flag after that, reaches its block only, and ends when archived. */
class HostelNoticeTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    @Test
    void aNoticeIsPublishedOnceAndKeepsItsEmergencyFlag() {
        HostelNotice notice = new HostelNotice(mock(Hostel.class), "B", "Water cut", "No water from 2 to 4 pm.", true, 1L);
        assertThat(notice.publish(NOW)).isTrue();
        assertThat(notice.publish(NOW.plusSeconds(60))).isFalse();
        assertThat(notice.getPublishedAt()).isEqualTo(NOW);
        notice.edit("Water cut", "No water from 2 to 5 pm.", true);
        assertThatThrownBy(() -> notice.edit("Water cut", "Fine", false)).isInstanceOf(IllegalStateException.class);
        notice.archive();
        assertThatThrownBy(() -> notice.publish(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aBlockNoticeReachesOnlyThatBlockAndAHostelNoticeEveryone() {
        HostelNotice block = new HostelNotice(mock(Hostel.class), "B", "Lift repair", "The lift is off today.", false, 1L);
        HostelNotice hostel = new HostelNotice(mock(Hostel.class), null, "Mess timings", "Dinner at 8.", false, 1L);
        assertThat(block.reaches("b")).isTrue();
        assertThat(block.reaches("A")).isFalse();
        assertThat(block.reaches(null)).isFalse();
        assertThat(hostel.reaches("A")).isTrue();
        assertThat(hostel.reaches(null)).isTrue();
    }
}
