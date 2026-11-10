package com.stacc.backend.lms.paper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
 * Editing a bank question and making final a paper that uses it, at the same moment, never both succeed: either the
 * edit finishes first and finalizing finds the question needing review, or finalizing finishes first and the edit finds
 * the final paper. Uses CS302's draft LMS course of faculty2, with its own questions and papers.
 */
class LmsExamPaperMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsExamPaperService papers;

    @Autowired
    private LmsQuestionBankService bank;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anEditAndAFinalizationAtOnceNeverBothSucceed() throws Exception {
        Long faculty = accounts.findByLoginId("faculty2").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS302' and lms.status = 'DRAFT'
                """, Long.class);

        for (int round = 0; round < 4; round++) {
            String original = "Paper question " + round;
            String edited = "Edited paper question " + round;
            Long questionId = bank.add(faculty, lmsCourseId, QuestionFixtures.singleChoice(original), null).getId();
            bank.markReviewed(faculty, questionId);
            Long paperId = papers.addPaper(faculty, lmsCourseId, "Race " + round, null, null).getId();
            papers.setQuestions(faculty, paperId,
                    List.of(new LmsExamPaperService.PaperQuestionInput(questionId, "Section A", new BigDecimal("2"))));

            List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                    () -> bank.edit(faculty, questionId, QuestionFixtures.singleChoice(edited), null),
                    () -> papers.finalizePaper(faculty, paperId));

            assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome ->
                    assertThat(outcome.failure()).isInstanceOfSatisfying(ApiException.class,
                            refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.CONFLICT)));

            Map<String, Object> question = jdbc.queryForMap(
                    "select question_text, review_status from lms_questions where id = ?", questionId);
            String paperStatus = jdbc.queryForObject("select status from lms_exam_papers where id = ?", String.class, paperId);
            if (outcomes.get(0).succeeded()) {
                assertThat(question).containsEntry("question_text", edited).containsEntry("review_status", "NEEDS_REVIEW");
                assertThat(paperStatus).isEqualTo("DRAFT");
            } else {
                assertThat(question).containsEntry("question_text", original).containsEntry("review_status", "REVIEWED");
                assertThat(paperStatus).isEqualTo("FINAL");
            }
        }
    }
}
