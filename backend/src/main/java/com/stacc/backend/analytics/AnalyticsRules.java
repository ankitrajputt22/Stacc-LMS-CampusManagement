package com.stacc.backend.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The aggregation rules, in one place. A class-level percentage or average is shown only for a group of at least
 * {@link #MIN_GROUP} students (otherwise null, with the reason); a person's own figures are always shown to them.
 * Results are rounded to one decimal place. Nothing here ranks or compares students.
 */
public final class AnalyticsRules {

    public static final int MIN_GROUP = 5;
    public static final String SMALL_GROUP = "Shown for groups of at least " + MIN_GROUP + " students.";

    private AnalyticsRules() {
    }

    /** part out of whole as a percentage, or null when there is nothing to divide. */
    public static BigDecimal percent(long part, long whole) {
        if (whole <= 0) {
            return null;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    /** As {@link #percent}, for a class view: null unless the group is large enough. */
    public static BigDecimal classPercent(long part, long whole, long groupSize) {
        return groupSize < MIN_GROUP ? null : percent(part, whole);
    }

    /** marks out of a maximum as a percentage. */
    public static BigDecimal percentOf(BigDecimal marks, BigDecimal max) {
        if (marks == null || max == null || max.signum() <= 0) {
            return null;
        }
        return marks.multiply(BigDecimal.valueOf(100)).divide(max, 1, RoundingMode.HALF_UP);
    }

    /** The mean of some percentages, or null for none. */
    public static BigDecimal mean(java.util.Collection<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    /** As {@link #mean}, for a class view: null unless at least {@link #MIN_GROUP} values went into it. */
    public static BigDecimal classMean(java.util.Collection<BigDecimal> values) {
        return values.size() < MIN_GROUP ? null : mean(values);
    }
}
