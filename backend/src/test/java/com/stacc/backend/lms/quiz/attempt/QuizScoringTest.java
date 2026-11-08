package com.stacc.backend.lms.quiz.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.lms.assignment.submission.SubmissionFixtures;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionContent;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionFixtures;
import com.stacc.backend.lms.question.QuestionType;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizQuestion;
import com.stacc.backend.lms.quiz.LmsQuizSettings;
import com.stacc.backend.lms.quiz.QuizResultRelease;
import com.stacc.backend.lms.quiz.QuizType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QuizScoringTest {

    private static final Instant NOW = Instant.parse("2026-11-10T05:00:00Z");
    private final LmsCourse course = SubmissionFixtures.course(7);
    private final CourseEnrollment enrollment = SubmissionFixtures.enrollment(course, 31, 41);
    private final LmsQuiz quiz = new LmsQuiz(course, 21L, new LmsQuizSettings("Q", null, LmsTextFormat.PLAIN, QuizType.GRADED,
            null, null, null, null, null, false, false, new BigDecimal("25")));
    private final LmsQuizAttempt attempt = new LmsQuizAttempt(quiz, enrollment, 1, NOW);
    private long nextId = 600;

    private LmsQuizQuestion question(LmsQuestionContent content, String marks) {
        LmsQuizQuestion link = new LmsQuizQuestion(quiz, new LmsQuestion(course, 21L, content), 1, new BigDecimal(marks));
        ReflectionTestUtils.setField(link, "id", nextId++);
        return link;
    }

    private LmsQuizAnswer answer(LmsQuizQuestion question, Set<Integer> positions, Boolean trueFalse, String text) {
        LmsQuizAnswer answer = new LmsQuizAnswer(attempt, question);
        ReflectionTestUtils.setField(answer, "id", nextId++);
        answer.replace(positions, trueFalse, text, NOW);
        return answer;
    }

    private static LmsQuestionContent content(QuestionType type, List<LmsQuestionContent.Option> options, Boolean trueFalse) {
        return new LmsQuestionContent(type, "Q", null, null, options, trueFalse, null, null, QuestionDifficulty.EASY,
                BigDecimal.ONE, null, null);
    }

    @Test
    void objectiveAnswersAreMarkedAutomaticallyWithNegativeMarkingForWrongOnes() {
        LmsQuizQuestion single = question(QuestionFixtures.singleChoice("Which?"), "2");
        LmsQuizQuestion multiple = question(content(QuestionType.MULTIPLE_CHOICE, List.of(QuestionFixtures.option("A", true),
                QuestionFixtures.option("B", true), QuestionFixtures.option("C", false)), null), "4");
        LmsQuizQuestion trueFalse = question(content(QuestionType.TRUE_FALSE, List.of(), true), "1");
        LmsQuizQuestion shortAnswer = question(content(QuestionType.SHORT_ANSWER, List.of(QuestionFixtures.option("Binary  Tree", true)),
                null), "3");
        LmsQuizQuestion skipped = question(content(QuestionType.TRUE_FALSE, List.of(), false), "1");
        Map<Long, LmsQuizAnswer> answers = new HashMap<>();
        answers.put(single.getId(), answer(single, Set.of(2), null, null));
        answers.put(multiple.getId(), answer(multiple, Set.of(1), null, null));
        answers.put(trueFalse.getId(), answer(trueFalse, Set.of(), true, null));
        answers.put(shortAnswer.getId(), answer(shortAnswer, Set.of(), null, "  binary tree "));

        QuizScoring.AttemptScore score = QuizScoring.score(quiz, List.of(single, multiple, trueFalse, shortAnswer, skipped), answers, Map.of());

        assertThat(score.questions()).extracting(QuizScoring.QuestionScore::outcome).containsExactly(QuizAnswerOutcome.CORRECT,
                QuizAnswerOutcome.INCORRECT, QuizAnswerOutcome.CORRECT, QuizAnswerOutcome.CORRECT, QuizAnswerOutcome.UNANSWERED);
        assertThat(score.questions().get(1).marks()).isEqualByComparingTo("-1.00");
        assertThat(score.fullyMarked()).isTrue();
        assertThat(score.score()).isEqualByComparingTo("5.00");
        assertThat(score.totalMarks()).isEqualByComparingTo("11.00");
        assertThat(score.count(QuizAnswerOutcome.CORRECT)).isEqualTo(3);
    }

    @Test
    void aDescriptiveAnswerWaitsForATeacherAndATeachersMarkReplacesAutomaticMarks() {
        LmsQuizQuestion descriptive = question(content(QuestionType.DESCRIPTIVE, List.of(), null), "5");
        LmsQuizQuestion single = question(QuestionFixtures.singleChoice("Which?"), "2");
        LmsQuizAnswer written = answer(descriptive, Set.of(), null, "Because.");
        LmsQuizAnswer wrong = answer(single, Set.of(1), null, null);
        Map<Long, LmsQuizAnswer> answers = Map.of(descriptive.getId(), written, single.getId(), wrong);

        QuizScoring.AttemptScore waiting = QuizScoring.score(quiz, List.of(descriptive, single), answers, Map.of());
        assertThat(waiting.fullyMarked()).isFalse();
        assertThat(waiting.score()).isNull();
        assertThat(waiting.count(QuizAnswerOutcome.NEEDS_MARKING)).isEqualTo(1);

        LmsQuizAnswerMark essay = new LmsQuizAnswerMark(written);
        essay.mark(new BigDecimal("3.5"), descriptive.getMarks(), "Good start.", 21L, NOW);
        LmsQuizAnswerMark regrade = new LmsQuizAnswerMark(wrong);
        regrade.mark(new BigDecimal("1"), single.getMarks(), null, 21L, NOW);
        QuizScoring.AttemptScore marked = QuizScoring.score(quiz, List.of(descriptive, single), answers,
                Map.of(written.getId(), essay, wrong.getId(), regrade));

        assertThat(marked.questions()).extracting(QuizScoring.QuestionScore::outcome)
                .containsExactly(QuizAnswerOutcome.MARKED, QuizAnswerOutcome.INCORRECT);
        assertThat(marked.automaticMarks()).isEqualByComparingTo("0");
        assertThat(marked.teacherMarks()).isEqualByComparingTo("4.50");
        assertThat(marked.score()).isEqualByComparingTo("4.50");
    }

    @Test
    void theScoreNeverGoesBelowZeroAndAnEmptyAnswerIsUnanswered() {
        LmsQuizQuestion single = question(QuestionFixtures.singleChoice("Which?"), "4");
        LmsQuizQuestion other = question(QuestionFixtures.singleChoice("Which else?"), "1");
        Map<Long, LmsQuizAnswer> answers = Map.of(single.getId(), answer(single, Set.of(1), null, null),
                other.getId(), answer(other, Set.of(), null, null));

        QuizScoring.AttemptScore score = QuizScoring.score(quiz, List.of(single, other), answers, Map.of());
        assertThat(score.questions().get(1).outcome()).isEqualTo(QuizAnswerOutcome.UNANSWERED);
        assertThat(score.automaticMarks()).isEqualByComparingTo("-1.00");
        assertThat(score.score()).isEqualByComparingTo("0");
    }

    @Test
    void resultsWaitForMarkingAndForTheDueDateWhenTheQuizSaysSo() {
        LmsQuizQuestion single = question(QuestionFixtures.singleChoice("Which?"), "2");
        QuizScoring.AttemptScore score = QuizScoring.score(quiz, List.of(single), Map.of(), Map.of());

        assertThat(QuizResultStatus.of(attempt, score, NOW)).isEqualTo(QuizResultStatus.NOT_HANDED_IN);
        attempt.submit(NOW);
        assertThat(QuizResultStatus.of(attempt, score, NOW)).isEqualTo(QuizResultStatus.RELEASED);

        quiz.edit(new LmsQuizSettings("Q", null, null, QuizType.GRADED, null, null, NOW.plusSeconds(60), null, null, false,
                false, new BigDecimal("25"), QuizResultRelease.AFTER_DUE_DATE, true));
        assertThat(QuizResultStatus.of(attempt, score, NOW)).isEqualTo(QuizResultStatus.WAITING_FOR_RELEASE);
        assertThat(QuizResultStatus.of(attempt, score, NOW.plusSeconds(60))).isEqualTo(QuizResultStatus.RELEASED);
        assertThat(quiz.isShowAnswers()).isTrue();
    }

    @Test
    void aTeachersMarksStayWithinTheQuestionsMarks() {
        LmsQuizQuestion single = question(QuestionFixtures.singleChoice("Which?"), "2");
        LmsQuizAnswerMark mark = new LmsQuizAnswerMark(answer(single, Set.of(1), null, null));

        for (String wrong : List.of("-0.5", "2.01", "1.555")) {
            assertThatThrownBy(() -> mark.mark(new BigDecimal(wrong), single.getMarks(), null, 21L, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        mark.mark(new BigDecimal("2"), single.getMarks(), "  ", 21L, NOW);
        assertThat(mark.getMarks()).isEqualByComparingTo("2.00");
        assertThat(mark.getFeedback()).isNull();
    }
}
