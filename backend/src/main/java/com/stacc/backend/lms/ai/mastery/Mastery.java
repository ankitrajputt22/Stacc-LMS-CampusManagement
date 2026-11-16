package com.stacc.backend.lms.ai.mastery;

import java.time.Instant;
import java.util.List;

/**
 * Mastery and confidence (v0.15.5), worked out by code from a page's evidence, never stored. Mastery is the average
 * score with recent evidence weighted more (each older piece counts 0.8 times the one after it, the 20 newest only);
 * confidence grows with the amount of evidence (n / (n + 3)). The level reads them together.
 */
public final class Mastery {

    static final int WINDOW = 20;
    static final double DECAY = 0.8;

    public enum Level { NOT_STARTED, LEARNING, PRACTISING, MASTERED }

    /** A page's mastery: 0 to 1, how sure that is (0 to 1), the level, how many pieces of evidence, and the latest. */
    public record Result(double mastery, double confidence, Level level, int evidence, Instant lastPractisedAt) {
    }

    private Mastery() {
    }

    /** From one page's evidence, newest first. */
    public static Result of(List<AiLearningEvidence> newestFirst) {
        if (newestFirst.isEmpty()) {
            return new Result(0, 0, Level.NOT_STARTED, 0, null);
        }
        double weighted = 0;
        double weights = 0;
        double weight = 1;
        for (AiLearningEvidence evidence : newestFirst.subList(0, Math.min(WINDOW, newestFirst.size()))) {
            weighted += evidence.getScore().doubleValue() * weight;
            weights += weight;
            weight *= DECAY;
        }
        double mastery = round(weighted / weights);
        int count = newestFirst.size();
        double confidence = round(count / (count + 3.0));
        Level level = mastery >= 0.8 && confidence >= 0.6 ? Level.MASTERED : mastery >= 0.5 ? Level.PRACTISING : Level.LEARNING;
        return new Result(mastery, confidence, level, count, newestFirst.get(0).getCreatedAt());
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
