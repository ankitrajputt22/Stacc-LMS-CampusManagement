package com.stacc.backend.lms.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.question.LmsQuestionBankService;
import com.stacc.backend.lms.question.QuestionFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Editing a bank question and publishing a quiz that uses it, at the same moment, never both succeed: either the edit
 * finishes first and publishing finds the question needing review, or publishing finishes first and the edit finds
 * the quiz published. Either way a published quiz asks exactly the reviewed question. Uses CS302's draft LMS course of
 * faculty2, whose question bank and quizzes no other test reads.
 */
class LmsQuizMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsQuizService quizzes;

    @Autowired
    private LmsQuestionBankService bank;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anEditAndAPublicationAtOnceNeverBothSucceed() throws Exception {
        Long faculty = accounts.findByLoginId("faculty2").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS302' and lms.status = 'DRAFT'
                """, Long.class);

        for (int round = 0; round < 4; round++) {
            String original = "Quiz question " + round;
            String edited = "Edited quiz question " + round;
            Long questionId = bank.add(faculty, lmsCourseId, QuestionFixtures.singleChoice(original), null).getId();
            bank.markReviewed(faculty, questionId);
            Long quizId = quizzes.create(faculty, lmsCourseId, QuizFixtures.graded("Race " + round)).getId();
            quizzes.addQuestion(faculty, quizId, questionId, null);

            List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                    () -> bank.edit(faculty, questionId, QuestionFixtures.singleChoice(edited), null),
                    () -> quizzes.publish(faculty, quizId));

            assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome ->
                    assertThat(outcome.failure()).isInstanceOfSatisfying(ApiException.class,
                            refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.CONFLICT)));

            Map<String, Object> question = jdbc.queryForMap(
                    "select question_text, review_status from lms_questions where id = ?", questionId);
            String quizStatus = jdbc.queryForObject("select status from lms_quizzes where id = ?", String.class, quizId);
            if (outcomes.get(0).succeeded()) {
                assertThat(question).containsEntry("question_text", edited).containsEntry("review_status", "NEEDS_REVIEW");
                assertThat(quizStatus).isEqualTo("DRAFT");
            } else {
                assertThat(question).containsEntry("question_text", original).containsEntry("review_status", "REVIEWED");
                assertThat(quizStatus).isEqualTo("PUBLISHED");
            }
        }
    }
}
