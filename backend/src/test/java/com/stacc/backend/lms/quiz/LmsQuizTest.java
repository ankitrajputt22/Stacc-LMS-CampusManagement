package com.stacc.backend.lms.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.QuestionFixtures;
import org.junit.jupiter.api.Test;

class LmsQuizTest {

    private static final Instant MONDAY = Instant.parse("2026-11-16T03:30:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-11-17T03:30:00Z");
    private static final Instant WEDNESDAY = Instant.parse("2026-11-18T03:30:00Z");

    private static LmsQuiz quiz(LmsQuizSettings settings) {
        return new LmsQuiz(ContentFixtures.lmsCourse(7), 21L, settings);
    }

    @Test
    void aNewQuizIsADraftWithItsSettings() {
        LmsQuiz quiz = quiz(QuizFixtures.withNegative(
                QuizFixtures.settings("  Unit test  ", QuizType.GRADED, MONDAY, TUESDAY, WEDNESDAY), "33.33"));

        assertThat(quiz.getStatus()).isEqualTo(LmsContentStatus.DRAFT);
        assertThat(quiz.getTitle()).isEqualTo("Unit test");
        assertThat(quiz.getInstructionsFormat()).isEqualTo(LmsTextFormat.FORMATTED);
        assertThat(quiz.getNegativeMarkPercent()).isEqualByComparingTo("33.33");
        assertThat(quiz.getNegativeMarkPercent().scale()).isEqualTo(2);
        assertThat(quiz.getPublishedAt()).isNull();
    }

    @Test
    void timesMustBeInOrderAndSettingsInRange() {
        assertThatThrownBy(() -> quiz(QuizFixtures.settings("Q", QuizType.GRADED, TUESDAY, null, TUESDAY)))
                .hasMessage("The closing time must be after the opening time.");
        assertThatThrownBy(() -> quiz(QuizFixtures.settings("Q", QuizType.GRADED, TUESDAY, MONDAY, null)))
                .hasMessage("The due date cannot be before the opening time.");
        assertThatThrownBy(() -> quiz(QuizFixtures.settings("Q", QuizType.GRADED, null, WEDNESDAY, TUESDAY)))
                .hasMessage("The due date cannot be after the closing time.");
        assertThatThrownBy(() -> quiz(QuizFixtures.withNegative(QuizFixtures.graded("Q"), "100.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quiz(QuizFixtures.withNegative(QuizFixtures.graded("Q"), "12.345")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quiz(new LmsQuizSettings("Q", null, null, QuizType.GRADED, 601, null, null, null, null,
                false, false, BigDecimal.ZERO))).hasMessage("The time limit must be between 1 and 600 minutes.");
        assertThatThrownBy(() -> quiz(new LmsQuizSettings("Q", null, null, QuizType.GRADED, null, null, null, null, 0,
                false, false, BigDecimal.ZERO))).hasMessage("The attempt limit must be between 1 and 100 attempts.");
        assertThatThrownBy(() -> quiz(QuizFixtures.settings("Q", QuizType.GRADED, Instant.parse("1999-12-31T00:00:00Z"), null, null)))
                .hasMessage("The opening time must be between the years 2000 and 9999.");
    }

    @Test
    void emptyOptionalSettingsMeanNoLimit() {
        LmsQuiz quiz = quiz(new LmsQuizSettings("Q", " ", null, QuizType.PRACTICE, null, null, null, null, null, true, true, null));

        assertThat(quiz.getInstructions()).isNull();
        assertThat(quiz.getInstructionsFormat()).isEqualTo(LmsTextFormat.PLAIN);
        assertThat(quiz.getTimeLimitMinutes()).isNull();
        assertThat(quiz.getMaxAttempts()).isNull();
        assertThat(quiz.getNegativeMarkPercent()).isEqualByComparingTo("0");
    }

    @Test
    void publishingKeepsTheTypeShufflingAndNegativeMarkingButNotTheRest() {
        LmsQuiz quiz = quiz(QuizFixtures.graded("Q"));
        quiz.publish(MONDAY);
        quiz.publish(TUESDAY);
        assertThat(quiz.getPublishedAt()).isEqualTo(MONDAY);

        quiz.edit(QuizFixtures.settings("Renamed", QuizType.GRADED, null, WEDNESDAY, null));
        assertThat(quiz.getTitle()).isEqualTo("Renamed");
        assertThat(quiz.getDueAt()).isEqualTo(WEDNESDAY);
        assertThat(quiz.getStatus()).isEqualTo(LmsContentStatus.PUBLISHED);

        assertThatThrownBy(() -> quiz.edit(QuizFixtures.practice("Q"))).isInstanceOf(IllegalStateException.class)
                .hasMessage(LmsQuiz.FIXED_AFTER_PUBLISHING);
        assertThatThrownBy(() -> quiz.edit(QuizFixtures.withNegative(QuizFixtures.graded("Q"), "25")))
                .isInstanceOf(IllegalStateException.class);
        // The same percentage written differently is no change.
        quiz.edit(QuizFixtures.withNegative(QuizFixtures.graded("Q"), "0.00"));
    }

    @Test
    void anArchivedQuizCanNoLongerChangeOrBePublished() {
        LmsQuiz quiz = quiz(QuizFixtures.graded("Q"));
        quiz.archive();

        assertThatThrownBy(() -> quiz.edit(QuizFixtures.graded("New"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> quiz.publish(MONDAY)).isInstanceOf(IllegalStateException.class);
        assertThat(quiz.getPublishedAt()).isNull();
    }

    @Test
    void quizQuestionMarksArePositiveWithTwoDecimals() {
        LmsQuiz quiz = quiz(QuizFixtures.graded("Q"));
        LmsQuestion question = new LmsQuestion(ContentFixtures.lmsCourse(7), 21L, QuestionFixtures.singleChoice("Which?"));
        LmsQuizQuestion link = new LmsQuizQuestion(quiz, question, 1, new BigDecimal("1.5"));

        assertThat(link.getMarks()).isEqualByComparingTo("1.50");
        assertThatThrownBy(() -> link.changeMarks(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> link.changeMarks(new BigDecimal("0.001"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> link.moveTo(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
