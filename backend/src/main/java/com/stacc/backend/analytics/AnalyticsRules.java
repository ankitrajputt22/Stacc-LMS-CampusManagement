package com.stacc.backend.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AnalyticsRules {

    public static final int MIN_GROUP = 5;
    public static final String SMALL_GROUP = "Shown for groups of at least " + MIN_GROUP + " students.";

    private AnalyticsRules() {
    }

    public static BigDecimal percent(long part, long whole) {
        if (whole <= 0) {
            return null;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal classPercent(long part, long whole, long groupSize) {
        return groupSize < MIN_GROUP ? null : percent(part, whole);
    }

    public static BigDecimal percentOf(BigDecimal marks, BigDecimal max) {
        if (marks == null || max == null || max.signum() <= 0) {
            return null;
        }
        return marks.multiply(BigDecimal.valueOf(100)).divide(max, 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal mean(java.util.Collection<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal classMean(java.util.Collection<BigDecimal> values) {
        return values.size() < MIN_GROUP ? null : mean(values);
    }
}
