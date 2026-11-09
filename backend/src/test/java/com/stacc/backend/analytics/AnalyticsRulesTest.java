package com.stacc.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class AnalyticsRulesTest {

    @Test
    void classFiguresNeedAGroupOfAtLeastFive() {
        assertThat(AnalyticsRules.classPercent(3, 4, 4)).isNull();
        assertThat(AnalyticsRules.classPercent(4, 5, 5)).isEqualByComparingTo("80.0");
        List<BigDecimal> four = List.of(new BigDecimal("50"), new BigDecimal("60"), new BigDecimal("70"), new BigDecimal("80"));
        assertThat(AnalyticsRules.classMean(four)).isNull();
        assertThat(AnalyticsRules.mean(four)).isEqualByComparingTo("65.0");
    }

    @Test
    void percentagesRoundToOneDecimalAndNothingDividesByZero() {
        assertThat(AnalyticsRules.percent(1, 3)).isEqualByComparingTo("33.3");
        assertThat(AnalyticsRules.percent(1, 0)).isNull();
        assertThat(AnalyticsRules.percentOf(new BigDecimal("7.50"), new BigDecimal("10.00"))).isEqualByComparingTo("75.0");
        assertThat(AnalyticsRules.percentOf(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
    }
}
