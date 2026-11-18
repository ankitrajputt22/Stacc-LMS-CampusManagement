package com.stacc.backend.lms.ai.mastery;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MasteryTest {

    private static List<AiLearningEvidence> newestFirst(double... scores) {
        List<AiLearningEvidence> pieces = new ArrayList<>();
        Instant at = Instant.parse("2026-11-16T05:00:00Z");
        for (double score : scores) {
            pieces.add(new AiLearningEvidence(7L, 11L, 31L, AiLearningEvidence.Kind.PRACTICE_ANSWER, BigDecimal.valueOf(score), at));
            at = at.minusSeconds(60);
        }
        return pieces;
    }

    @Test
    void nothingYetIsNotStarted() {
        assertThat(Mastery.of(List.of()).level()).isEqualTo(Mastery.Level.NOT_STARTED);
    }

    @Test
    void recentEvidenceCountsMore() {
        assertThat(Mastery.of(newestFirst(1, 0)).mastery()).isGreaterThan(Mastery.of(newestFirst(0, 1)).mastery());
    }

    @Test
    void masteredNeedsHighScoresAndEnoughEvidence() {
        assertThat(Mastery.of(newestFirst(1)).level()).isEqualTo(Mastery.Level.PRACTISING);
        assertThat(Mastery.of(newestFirst(1, 1, 1, 1, 1)).level()).isEqualTo(Mastery.Level.MASTERED);
        assertThat(Mastery.of(newestFirst(1, 1, 1, 1, 1)).confidence()).isEqualTo(0.63);
        assertThat(Mastery.of(newestFirst(0.2, 0.3, 0.1)).level()).isEqualTo(Mastery.Level.LEARNING);
    }

    @Test
    void scoresAreClampedToZeroToOne() {
        AiLearningEvidence piece = new AiLearningEvidence(7L, 11L, 31L, AiLearningEvidence.Kind.FLASHCARD_REVIEW, BigDecimal.valueOf(1.7),
                Instant.EPOCH);
        assertThat(piece.getScore()).isEqualByComparingTo("1.000");
    }
}
