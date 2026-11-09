package com.stacc.backend.lms.assignment.rubric;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.lms.course.LmsCourse;
import org.springframework.test.util.ReflectionTestUtils;

/** Rubrics for tests. */
public final class RubricFixtures {

    private RubricFixtures() {
    }

    /** Two criteria worth the given points each, the first with two levels. */
    public static RubricContent content(String title, String first, String second) {
        return new RubricContent(title, null, List.of(
                new RubricContent.Criterion("Design", null, new BigDecimal(first), List.of(
                        new RubricContent.Level("Excellent", null, new BigDecimal(first)),
                        new RubricContent.Level("Needs work", null, BigDecimal.ONE))),
                new RubricContent.Criterion("Explanation", "Says why.", new BigDecimal(second), List.of())));
    }

    /** A saved rubric of the course, with IDs on it and its criteria (criteria get id, id + 1). */
    public static LmsRubric rubric(LmsCourse course, long id, String first, String second) {
        LmsRubric rubric = new LmsRubric(course, 21L, content("Rubric " + id, first, second));
        ReflectionTestUtils.setField(rubric, "id", id);
        long criterionId = id * 10;
        for (LmsRubricCriterion criterion : rubric.getCriteria()) {
            ReflectionTestUtils.setField(criterion, "id", criterionId++);
        }
        return rubric;
    }
}
