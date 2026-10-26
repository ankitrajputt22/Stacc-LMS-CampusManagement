package com.stacc.backend.lms.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stacc.backend.lms.content.ContentFixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AiCourseKnowledgeIndexTest {

    private static final String FIRST = "a".repeat(64);
    private static final String CHANGED = "b".repeat(64);
    private static final Instant AT = Instant.parse("2026-10-27T09:00:00Z");

    private static AiCourseKnowledgeIndex index() {
        return new AiCourseKnowledgeIndex(ContentFixtures.lmsCourse(1));
    }

    @Test
    void aFirstRefreshBuildsAndAnswersNothingYet() {
        AiCourseKnowledgeIndex index = index();
        assertThat(index.getStatus()).isEqualTo(AiKnowledgeIndexStatus.BUILDING);
        assertThat(index.answersFor(FIRST)).isFalse();
        assertThat(index.isStaleFor(FIRST)).isFalse();
    }

    @Test
    void readyAnswersOnlyForTheIndexedContent() {
        AiCourseKnowledgeIndex index = index();
        index.ready(FIRST, AT);
        assertThat(index.answersFor(FIRST)).isTrue();
        assertThat(index.isStaleFor(FIRST)).isFalse();
        // Published content changed: stale, and students wait for a refresh.
        assertThat(index.isStaleFor(CHANGED)).isTrue();
        assertThat(index.answersFor(CHANGED)).isFalse();
    }

    @Test
    void aRebuildKeepsThePreviousIndexAnsweringButAFailureStopsIt() {
        AiCourseKnowledgeIndex index = index();
        index.ready(FIRST, AT);
        index.startBuilding();
        assertThat(index.answersFor(FIRST)).isTrue();
        index.failed("The AI service was unavailable.");
        assertThat(index.getStatus()).isEqualTo(AiKnowledgeIndexStatus.FAILED);
        assertThat(index.answersFor(FIRST)).isFalse();
        // The last successful fingerprint and time are kept.
        assertThat(index.getContentFingerprint()).isEqualTo(FIRST);
        assertThat(index.getIndexedAt()).isEqualTo(AT);
        index.startBuilding();
        assertThat(index.getLastError()).isNull();
        index.ready(CHANGED, AT.plusSeconds(60));
        assertThat(index.answersFor(CHANGED)).isTrue();
    }

    @Test
    void lastErrorIsBoundedAndFingerprintsAreChecked() {
        AiCourseKnowledgeIndex index = index();
        index.failed("x".repeat(5000));
        assertThat(index.getLastError()).hasSize(AiCourseKnowledgeIndex.MAX_ERROR_LENGTH);
        assertThatThrownBy(() -> index.ready("not-a-fingerprint", AT)).isInstanceOf(IllegalArgumentException.class);
    }
}
