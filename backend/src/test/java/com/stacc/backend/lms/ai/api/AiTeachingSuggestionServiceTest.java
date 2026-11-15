package com.stacc.backend.lms.ai.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.lms.ai.AiServiceMessages.RubricCriterion;
import com.stacc.backend.lms.ai.AiServiceMessages.RubricLevel;
import com.stacc.backend.lms.ai.AiServiceMessages.RubricResult;
import org.junit.jupiter.api.Test;

/** Suggested rubrics always add up to the assignment's maximum, with levels within their criterion, or are left out. */
class AiTeachingSuggestionServiceTest {

    private static RubricCriterion criterion(String title, String points, RubricLevel... levels) {
        return new RubricCriterion(title, null, new BigDecimal(points), List.of(levels));
    }

    @Test
    void theLastCriterionTakesTheRoundingAndLevelsAreCapped() {
        RubricResult result = new RubricResult(List.of(
                criterion("Analysis", "3.333", new RubricLevel("Excellent", null, new BigDecimal("9"))),
                criterion("Writing", "6.667", new RubricLevel("Good", null, new BigDecimal("6")), new RubricLevel(" ", null, BigDecimal.ONE))),
                List.of(), false);
        List<AiRubricSuggestionResponse.Criterion> criteria = AiTeachingSuggestionService.criteria(result, new BigDecimal("10.00"));
        assertThat(criteria).extracting(AiRubricSuggestionResponse.Criterion::maxPoints)
                .containsExactly(new BigDecimal("3.33"), new BigDecimal("6.67"));
        assertThat(criteria.get(0).levels().get(0).points()).isEqualByComparingTo("3.33");
        assertThat(criteria.get(1).levels()).hasSize(1);
    }

    @Test
    void criteriaThatOverrunTheMaximumAreLeftOut() {
        RubricResult result = new RubricResult(List.of(criterion("A", "8"), criterion("B", "5")), List.of(), false);
        assertThat(AiTeachingSuggestionService.criteria(result, new BigDecimal("6"))).isEmpty();
        assertThat(AiTeachingSuggestionService.criteria(new RubricResult(List.of(), List.of(), true), BigDecimal.TEN)).isEmpty();
    }
}
