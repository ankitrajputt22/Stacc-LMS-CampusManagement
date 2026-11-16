package com.stacc.backend.academic.result;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * The college's grade scale (v0.16.3), read once per request: grades with their points and lowest percentages, best
 * first. A result's grade is the code entered when the scale knows it, otherwise the grade its percentage reaches; a
 * code the scale does not know leaves the result out of grade points. Nothing here is stored.
 */
public final class GradeScale {

    public record Grade(String code, BigDecimal points, BigDecimal minPercent, boolean passing) {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final List<Grade> grades;

    public GradeScale(List<Grade> grades) {
        this.grades = List.copyOf(grades);
    }

    public static GradeScale of(List<GradeScaleEntry> entries) {
        return new GradeScale(entries.stream()
                .map(entry -> new Grade(entry.getGradeCode(), entry.getGradePoints(), entry.getMinPercent(), entry.isPassing()))
                .toList());
    }

    public List<Grade> grades() {
        return grades;
    }

    /** The marks as a percentage of the maximum, to two decimal places. */
    public static BigDecimal percent(BigDecimal marks, BigDecimal max) {
        return marks.multiply(HUNDRED).divide(max, 2, RoundingMode.HALF_UP);
    }

    /** The best grade whose lowest percentage the given percentage reaches. */
    public Optional<Grade> forPercent(BigDecimal percent) {
        return grades.stream().filter(grade -> percent.compareTo(grade.minPercent()) >= 0).findFirst();
    }

    /** The grade a result counts as: its entered code when the scale knows it, its percentage's grade when none. */
    public Optional<Grade> gradeOf(String gradeCode, BigDecimal marks, BigDecimal max) {
        if (gradeCode != null && !gradeCode.isBlank()) {
            return grades.stream().filter(grade -> grade.code().equalsIgnoreCase(gradeCode.strip())).findFirst();
        }
        return forPercent(percent(marks, max));
    }
}
