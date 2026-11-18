package com.stacc.backend.lms.ai.flashcards;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SpacedRepetition {

    public static final BigDecimal START_EASE = new BigDecimal("2.50");
    static final BigDecimal MIN_EASE = new BigDecimal("1.30");
    static final BigDecimal MAX_EASE = new BigDecimal("3.00");
    static final int MAX_INTERVAL_DAYS = 365;

    public record State(BigDecimal ease, int intervalDays, int repetitions, int lapses) {
    }

    private SpacedRepetition() {
    }

    public static State next(State state, ReviewRating rating) {
        BigDecimal ease = state.ease();
        int interval;
        int repetitions = state.repetitions();
        int lapses = state.lapses();
        switch (rating) {
            case AGAIN -> {
                ease = clamp(ease.subtract(new BigDecimal("0.20")));
                interval = 0;
                repetitions = 0;
                lapses++;
            }
            case HARD -> {
                ease = clamp(ease.subtract(new BigDecimal("0.15")));
                interval = Math.max(1, (int) Math.round(state.intervalDays() * 1.2));
                repetitions++;
            }
            case GOOD -> {
                interval = grown(state, ease);
                repetitions++;
            }
            default -> {
                interval = (int) Math.round(grown(state, ease) * 1.3);
                ease = clamp(ease.add(new BigDecimal("0.15")));
                repetitions++;
            }
        }
        return new State(ease, Math.min(interval, MAX_INTERVAL_DAYS), repetitions, lapses);
    }

    private static int grown(State state, BigDecimal ease) {
        if (state.repetitions() == 0) {
            return 1;
        }
        if (state.repetitions() == 1) {
            return 3;
        }
        return Math.max(state.intervalDays() + 1, (int) Math.round(state.intervalDays() * ease.doubleValue()));
    }

    private static BigDecimal clamp(BigDecimal ease) {
        return ease.max(MIN_EASE).min(MAX_EASE).setScale(2, RoundingMode.HALF_UP);
    }
}
