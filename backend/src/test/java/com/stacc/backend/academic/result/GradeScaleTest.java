package com.stacc.backend.academic.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class GradeScaleTest {

    private final GradeScale scale = new GradeScale(List.of(
            new GradeScale.Grade("O", new BigDecimal("10"), new BigDecimal("90"), true),
            new GradeScale.Grade("A+", new BigDecimal("9"), new BigDecimal("80"), true),
            new GradeScale.Grade("A", new BigDecimal("8"), new BigDecimal("70"), true),
            new GradeScale.Grade("P", new BigDecimal("4"), new BigDecimal("40"), true),
            new GradeScale.Grade("F", BigDecimal.ZERO, BigDecimal.ZERO, false)));

    @Test
    void anEnteredGradeOnTheScaleIsUsedAsItIs() {
        assertThat(scale.gradeOf("a+", new BigDecimal("71"), new BigDecimal("100"))).get()
                .extracting(GradeScale.Grade::code).isEqualTo("A+");
    }

    @Test
    void withoutAGradeThePercentageDecides() {
        assertThat(scale.gradeOf(null, new BigDecimal("78"), new BigDecimal("100"))).get().extracting(GradeScale.Grade::code).isEqualTo("A");
        assertThat(scale.gradeOf(null, new BigDecimal("36"), new BigDecimal("40"))).get().extracting(GradeScale.Grade::code).isEqualTo("O");
        assertThat(scale.gradeOf(null, new BigDecimal("12"), new BigDecimal("100"))).get().extracting(GradeScale.Grade::passing).isEqualTo(false);
    }

    @Test
    void aCodeTheScaleDoesNotKnowCountsForNothing() {
        assertThat(scale.gradeOf("Distinction", new BigDecimal("95"), new BigDecimal("100"))).isEmpty();
    }
}
