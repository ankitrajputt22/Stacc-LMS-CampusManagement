package com.stacc.backend.lms.quiz.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.question.LmsQuestionBankService;
import com.stacc.backend.lms.question.LmsQuestionContent;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionType;
import com.stacc.backend.lms.quiz.LmsQuizService;
import com.stacc.backend.lms.quiz.LmsQuizSettings;
import com.stacc.backend.lms.quiz.QuizType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two teachers marking the same answer at once both succeed, one after the other under the attempt row lock, leaving
 * exactly one mark with one of their values. Uses its own quiz in CS301, taken by studentfaculty1, whom no other quiz
 * test uses.
 */
class LmsQuizMarkingMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsQuizAttemptService attempts;

    @Autowired
    private LmsQuizGradingService grading;

    @Autowired
    private LmsQuizService quizzes;

    @Autowired
    private LmsQuestionBankService bank;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void simultaneousMarksLeaveOneMark() throws Exception {
        Long faculty = accounts.findByLoginId("faculty1").orElseThrow().getId();
        Long student = accounts.findByLoginId("studentfaculty1").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS301' and lms.status = 'PUBLISHED'
                """, Long.class);
        Long questionId = bank.add(faculty, lmsCourseId, new LmsQuestionContent(QuestionType.DESCRIPTIVE, "Marking race essay",
                LmsTextFormat.PLAIN, null, List.of(), null, null, null, QuestionDifficulty.EASY, new BigDecimal("5"), null, null),
                null).getId();
        bank.markReviewed(faculty, questionId);
        Long quizId = quizzes.create(faculty, lmsCourseId, new LmsQuizSettings("Marking race", null, LmsTextFormat.PLAIN,
                QuizType.GRADED, null, null, null, null, null, false, false, BigDecimal.ZERO)).getId();
        Long quizQuestionId = quizzes.addQuestion(faculty, quizId, questionId, null).questions().getFirst().getId();
        quizzes.publish(faculty, quizId);
        Long attemptId = attempts.start(student, lmsCourseId, quizId).getId();
        attempts.saveAnswer(student, attemptId, quizQuestionId, new LmsQuizAttemptService.AnswerInput(null, null, "An answer."));
        attempts.submit(student, attemptId);

        List<Concurrently.Outcome<LmsQuizGradingService.AttemptReview>> outcomes = Concurrently.run(
                () -> grading.mark(faculty, attemptId, quizQuestionId, new BigDecimal("3"), "First"),
                () -> grading.mark(faculty, attemptId, quizQuestionId, new BigDecimal("4"), "Second"));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        assertThat(jdbc.queryForList("""
                select mark.marks from lms_quiz_answer_marks mark join lms_quiz_answers answer on answer.id = mark.lms_quiz_answer_id
                where answer.lms_quiz_attempt_id = ?
                """, BigDecimal.class, attemptId)).singleElement().satisfies(marks -> assertThat(marks).isIn(new BigDecimal("3.00"),
                new BigDecimal("4.00")));
        assertThat(grading.review(faculty, attemptId).score().score()).isIn(new BigDecimal("3.00"), new BigDecimal("4.00"));
    }
}
