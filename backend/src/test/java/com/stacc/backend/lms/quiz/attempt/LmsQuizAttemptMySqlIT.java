package com.stacc.backend.lms.quiz.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.question.LmsQuestionBankService;
import com.stacc.backend.lms.question.QuestionFixtures;
import com.stacc.backend.lms.quiz.LmsQuizService;
import com.stacc.backend.lms.quiz.LmsQuizSettings;
import com.stacc.backend.lms.quiz.QuizType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starting twice at once makes one attempt (the second start resumes it), and an answer saved while the attempt is handed
 * in is either taken before the hand-in or refused after it. Uses its own quiz in CS301, taken by student2, whom no
 * other quiz test uses.
 */
class LmsQuizAttemptMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsQuizAttemptService attempts;

    @Autowired
    private LmsQuizService quizzes;

    @Autowired
    private LmsQuestionBankService bank;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void simultaneousStartsMakeOneAttemptAndNoAnswerIsTakenAfterHandingIn() throws Exception {
        Long faculty = accounts.findByLoginId("faculty1").orElseThrow().getId();
        Long student = accounts.findByLoginId("student2").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS301' and lms.status = 'PUBLISHED'
                """, Long.class);
        Long questionId = bank.add(faculty, lmsCourseId, QuestionFixtures.singleChoice("Attempt race question"), null).getId();
        bank.markReviewed(faculty, questionId);
        Long quizId = quizzes.create(faculty, lmsCourseId, new LmsQuizSettings("Attempt race", null, LmsTextFormat.PLAIN,
                QuizType.GRADED, null, null, null, null, 3, false, false, BigDecimal.ZERO)).getId();
        Long quizQuestionId = quizzes.addQuestion(faculty, quizId, questionId, null).questions().getFirst().getId();
        quizzes.publish(faculty, quizId);

        List<Concurrently.Outcome<LmsQuizAttempt>> starts = Concurrently.run(
                () -> attempts.start(student, lmsCourseId, quizId),
                () -> attempts.start(student, lmsCourseId, quizId),
                () -> attempts.start(student, lmsCourseId, quizId));
        assertThat(starts).allMatch(Concurrently.Outcome::succeeded);
        assertThat(starts).extracting(outcome -> outcome.result().getId()).containsOnly(starts.getFirst().result().getId());
        Long attemptId = starts.getFirst().result().getId();
        assertThat(jdbc.queryForObject("select count(*) from lms_quiz_attempts where lms_quiz_id = ?", Integer.class, quizId))
                .isEqualTo(1);

        List<Concurrently.Outcome<Object>> race = Concurrently.run(
                () -> attempts.saveAnswer(student, attemptId, quizQuestionId,
                        new LmsQuizAttemptService.AnswerInput(List.of(2), null, null)),
                () -> attempts.submit(student, attemptId));
        assertThat(race.get(1).succeeded()).isTrue();
        assertThat(jdbc.queryForObject("select status from lms_quiz_attempts where id = ?", String.class, attemptId))
                .isEqualTo("SUBMITTED");
        int saved = jdbc.queryForObject("select count(*) from lms_quiz_answers where lms_quiz_attempt_id = ?", Integer.class, attemptId);
        int savedBeforeHandIn = jdbc.queryForObject("""
                select count(*) from lms_quiz_answers answer join lms_quiz_attempts attempt on attempt.id = answer.lms_quiz_attempt_id
                where attempt.id = ? and answer.answered_at <= attempt.submitted_at
                """, Integer.class, attemptId);
        if (race.getFirst().succeeded()) {
            assertThat(saved).isEqualTo(1);
            assertThat(savedBeforeHandIn).isEqualTo(1);
        } else {
            assertThat(race.getFirst().failure()).isInstanceOfSatisfying(ApiException.class,
                    refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.CONFLICT));
            assertThat(saved).isZero();
        }
    }

    @Test
    void twoStudentsStartingAndOneSavingTheSameAnswerAtOnceNeverFail() throws Exception {
        Long faculty = accounts.findByLoginId("faculty1").orElseThrow().getId();
        Long first = accounts.findByLoginId("student1").orElseThrow().getId();
        Long second = accounts.findByLoginId("studentfaculty1").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS301' and lms.status = 'PUBLISHED'
                """, Long.class);
        Long questionId = bank.add(faculty, lmsCourseId, QuestionFixtures.singleChoice("Burst race question"), null).getId();
        bank.markReviewed(faculty, questionId);
        Long quizId = quizzes.create(faculty, lmsCourseId, new LmsQuizSettings("Burst race", null, LmsTextFormat.PLAIN,
                QuizType.GRADED, null, null, null, null, 1, false, false, BigDecimal.ZERO)).getId();
        Long quizQuestionId = quizzes.addQuestion(faculty, quizId, questionId, null).questions().getFirst().getId();
        quizzes.publish(faculty, quizId);

        // Two students' first starts share a gap of the attempts index; neither may deadlock the other.
        List<Concurrently.Outcome<LmsQuizAttempt>> starts = Concurrently.run(
                () -> attempts.start(first, lmsCourseId, quizId),
                () -> attempts.start(second, lmsCourseId, quizId));
        assertThat(starts).allMatch(Concurrently.Outcome::succeeded);
        Long attemptId = starts.getFirst().result().getId();

        // The same choice saved four times at once: one answer row with one chosen option, never a duplicate.
        List<Concurrently.Outcome<LmsQuizAnswer>> saves = Concurrently.run(
                () -> attempts.saveAnswer(first, attemptId, quizQuestionId, new LmsQuizAttemptService.AnswerInput(List.of(2), null, null)),
                () -> attempts.saveAnswer(first, attemptId, quizQuestionId, new LmsQuizAttemptService.AnswerInput(List.of(2), null, null)),
                () -> attempts.saveAnswer(first, attemptId, quizQuestionId, new LmsQuizAttemptService.AnswerInput(List.of(2), null, null)),
                () -> attempts.saveAnswer(first, attemptId, quizQuestionId, new LmsQuizAttemptService.AnswerInput(List.of(2), null, null)));
        assertThat(saves).allMatch(Concurrently.Outcome::succeeded);
        assertThat(jdbc.queryForObject("""
                select count(*) from lms_quiz_answer_options chosen join lms_quiz_answers answer on answer.id = chosen.lms_quiz_answer_id
                where answer.lms_quiz_attempt_id = ?
                """, Integer.class, attemptId)).isEqualTo(1);
    }
}
