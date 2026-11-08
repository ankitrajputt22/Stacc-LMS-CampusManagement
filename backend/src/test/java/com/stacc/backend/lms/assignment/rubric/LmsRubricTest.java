package com.stacc.backend.lms.assignment.rubric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.submission.SubmissionFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.Test;

class LmsRubricTest {

    private final LmsCourse course = SubmissionFixtures.course(7);

    @Test
    void aRubricKeepsItsCriteriaInOrderWithTheirLevelsAndTotal() {
        LmsRubric rubric = new LmsRubric(course, 21L, RubricFixtures.content("  Design  ", "12.5", "7.5"));

        assertThat(rubric.getTitle()).isEqualTo("Design");
        assertThat(rubric.getCriteria()).extracting(LmsRubricCriterion::getTitle).containsExactly("Design", "Explanation");
        assertThat(rubric.getCriteria()).extracting(LmsRubricCriterion::getPosition).containsExactly(1, 2);
        assertThat(rubric.getCriteria().getFirst().getLevels()).extracting(LmsRubricLevel::getTitle).containsExactly("Excellent", "Needs work");
        assertThat(rubric.getTotalPoints()).isEqualByComparingTo("20");
    }

    @Test
    void criteriaAndLevelsMustFit() {
        assertThatThrownBy(() -> new LmsRubric(course, 21L, new RubricContent("R", null, List.of())))
                .hasMessage("A rubric needs 1 to 20 criteria.");
        assertThatThrownBy(() -> new LmsRubric(course, 21L, new RubricContent("R", null, Collections.nCopies(21,
                new RubricContent.Criterion("C", null, BigDecimal.ONE, List.of())))))
                .hasMessage("A rubric needs 1 to 20 criteria.");
        assertThatThrownBy(() -> new LmsRubric(course, 21L, new RubricContent("R", null, List.of(
                new RubricContent.Criterion("Same", null, BigDecimal.ONE, List.of()),
                new RubricContent.Criterion("same", null, BigDecimal.ONE, List.of())))))
                .hasMessage("Criteria must have different titles.");
        assertThatThrownBy(() -> new LmsRubric(course, 21L, new RubricContent("R", null, List.of(
                new RubricContent.Criterion("C", null, BigDecimal.ONE, List.of(new RubricContent.Level("Too much", null, BigDecimal.TEN)))))))
                .hasMessage("Criterion 1 has a level worth more than its maximum points.");
        assertThatThrownBy(() -> new LmsRubric(course, 21L, new RubricContent("R", null, List.of(
                new RubricContent.Criterion("C", null, BigDecimal.ZERO, List.of())))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anArchivedRubricCanNoLongerChange() {
        LmsRubric rubric = new LmsRubric(course, 21L, RubricFixtures.content("R", "10", "10"));
        rubric.archive();
        assertThatThrownBy(() -> rubric.edit(RubricFixtures.content("New", "10", "10"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anAssignmentUsesARubricOfItsCourseWhoseTotalEqualsItsMarks() {
        LmsAssignment assignment = SubmissionFixtures.assignment(course, 5, LmsContentStatus.PUBLISHED);

        assertThatThrownBy(() -> assignment.useRubric(RubricFixtures.rubric(course, 1, "10", "5")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The rubric's total (15) must equal the assignment's maximum marks (20).");
        assertThatThrownBy(() -> assignment.useRubric(RubricFixtures.rubric(SubmissionFixtures.course(8), 2, "10", "10")))
                .isInstanceOf(IllegalArgumentException.class);

        assignment.useRubric(RubricFixtures.rubric(course, 3, "10", "10"));
        assertThat(assignment.getRubric().getId()).isEqualTo(3L);
        assertThatThrownBy(() -> assignment.edit("A", "B", SubmissionFixtures.DUE, new BigDecimal("25")))
                .hasMessage(LmsAssignment.RUBRIC_TOTAL_FIXED);
        assignment.useRubric(null);
        assignment.edit("A", "B", SubmissionFixtures.DUE, new BigDecimal("25"));
    }
}
