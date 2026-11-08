package com.stacc.backend.lms.question;

import static com.stacc.backend.lms.question.QuestionFixtures.of;
import static com.stacc.backend.lms.question.QuestionFixtures.option;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.outcome.LmsCourseOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LmsQuestionTest {

    private static LmsQuestion question(LmsQuestionContent content) {
        return new LmsQuestion(ContentFixtures.lmsCourse(7), 21L, content);
    }

    private static void refused(LmsQuestionContent content, String message) {
        assertThatThrownBy(() -> question(content)).isInstanceOf(IllegalArgumentException.class).hasMessage(message);
    }

    @Test
    void aNewQuestionNeedsReviewAndIsNotOfferedForPractice() {
        LmsQuestion question = question(QuestionFixtures.singleChoice("  Which normal form removes partial dependencies?  "));

        assertThat(question.getText()).isEqualTo("Which normal form removes partial dependencies?");
        assertThat(question.getTextFormat()).isEqualTo(LmsTextFormat.FORMATTED);
        assertThat(question.getOptions()).extracting(LmsQuestionOption::getPosition).containsExactly(1, 2);
        assertThat(question.getOptions()).extracting(LmsQuestionOption::isCorrect).containsExactly(false, true);
        assertThat(question.getMarks()).isEqualTo(new BigDecimal("2.00"));
        assertThat(question.getReviewStatus()).isEqualTo(QuestionReviewStatus.NEEDS_REVIEW);
        assertThat(question.isPracticeAllowed()).isFalse();
        assertThat(question.isActive()).isTrue();
    }

    @Test
    void eachTypeKeepsItsAnswersItsOwnWay() {
        refused(of(QuestionType.SINGLE_CHOICE, List.of(option("Only", true)), null, null), "A choice question needs 2 to 10 options.");
        refused(of(QuestionType.SINGLE_CHOICE, List.of(option("A", true), option("B", true)), null, null),
                "A single-choice question needs exactly one correct option.");
        refused(of(QuestionType.MULTIPLE_CHOICE, List.of(option("A", false), option("B", false)), null, null),
                "A multiple-choice question needs at least one correct option.");
        refused(of(QuestionType.MULTIPLE_CHOICE, List.of(option("Same", true), option(" same ", false)), null, null),
                "Options must differ from each other.");
        refused(of(QuestionType.TRUE_FALSE, List.of(), null, null), "The true/false answer is required");
        refused(of(QuestionType.TRUE_FALSE, List.of(option("True", true)), true, null), "This type of question takes no options.");
        refused(of(QuestionType.SHORT_ANSWER, List.of(), null, null), "A short-answer question needs 1 to 10 accepted answers.");
        refused(of(QuestionType.SHORT_ANSWER, List.of(option("2NF", false)), null, null), "Every accepted answer must be marked correct.");
        refused(of(QuestionType.DESCRIPTIVE, List.of(), true, null), "Only a true/false question takes a true/false answer.");
        refused(of(QuestionType.SINGLE_CHOICE, List.of(option("A", true), option("B", false)), null, "Look for keys"),
                "Only a descriptive question takes answer guidance.");

        assertThat(question(of(QuestionType.MULTIPLE_CHOICE, List.of(option("A", true), option("B", true)), null, null))
                .getOptions()).hasSize(2);
        assertThat(question(of(QuestionType.TRUE_FALSE, null, false, null)).getTrueFalseAnswer()).isFalse();
        assertThat(question(of(QuestionType.SHORT_ANSWER, List.of(option("2NF", true), option("Second normal form", true)), null, null))
                .getOptions()).hasSize(2);
        assertThat(question(of(QuestionType.DESCRIPTIVE, List.of(), null, "Mention keys.")).getAnswerGuidance()).isEqualTo("Mention keys.");
    }

    @Test
    void marksArePositiveWithTwoDecimalPlaces() {
        LmsQuestionContent base = QuestionFixtures.singleChoice("Q");
        for (String wrong : List.of("0", "-1", "1.005", "1000000")) {
            LmsQuestionContent content = new LmsQuestionContent(base.type(), base.text(), null, null, base.options(), null, null, null,
                    base.difficulty(), new BigDecimal(wrong), null, null);
            assertThatThrownBy(() -> question(content)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void anOutcomeMustBelongToTheSameCourse() {
        LmsCourseOutcome elsewhere = new LmsCourseOutcome(ContentFixtures.lmsCourse(8), 1, "Elsewhere");
        refused(QuestionFixtures.singleChoice("Q").withOutcome(elsewhere), "The outcome must belong to the question's course.");
    }

    @Test
    void editingNeedsANewReviewAndEndsPracticeAndTheFormatIsKeptWhenLeftOut() {
        LmsQuestion question = question(QuestionFixtures.singleChoice("Q"));
        question.markReviewed();
        question.allowPractice(true);
        assertThat(question.isPracticeAllowed()).isTrue();

        question.edit(of(QuestionType.TRUE_FALSE, List.of(), true, null));

        assertThat(question.getType()).isEqualTo(QuestionType.TRUE_FALSE);
        assertThat(question.getOptions()).isEmpty();
        assertThat(question.getTextFormat()).isEqualTo(LmsTextFormat.FORMATTED);
        assertThat(question.getReviewStatus()).isEqualTo(QuestionReviewStatus.NEEDS_REVIEW);
        assertThat(question.isPracticeAllowed()).isFalse();
    }

    @Test
    void onlyAReviewedActiveQuestionIsOfferedForPracticeAndArchivingIsFinal() {
        LmsQuestion question = question(QuestionFixtures.singleChoice("Q"));
        assertThatThrownBy(() -> question.allowPractice(true)).isInstanceOf(IllegalStateException.class);
        question.markReviewed();
        question.allowPractice(true);

        question.archive();
        question.archive();

        assertThat(question.getStatus()).isEqualTo(QuestionStatus.ARCHIVED);
        assertThat(question.isPracticeAllowed()).isFalse();
        assertThatThrownBy(question::markReviewed).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> question.edit(QuestionFixtures.singleChoice("Again"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anArchivedOutcomeAlreadyChosenMayStayButTheCourseMustMatch() {
        LmsCourseOutcome outcome = new LmsCourseOutcome(ContentFixtures.lmsCourse(7), 1, "Model data.");
        ReflectionTestUtils.setField(outcome, "id", 91L);
        outcome.archive();
        assertThat(question(QuestionFixtures.singleChoice("Q").withOutcome(outcome)).getOutcome()).isSameAs(outcome);
    }
}
