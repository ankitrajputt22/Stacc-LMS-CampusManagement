package com.stacc.backend.lms.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * An edit and a practice change at the same moment never overwrite each other: both lock the question row first, so an
 * edited question always ends up needing review and not offered for practice. Uses CS250's draft LMS course of faculty2,
 * whose question bank no other test touches.
 */
class LmsQuestionBankMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsQuestionBankService service;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anEditAndAPracticeChangeAtOnceLeaveTheEditedQuestionNeedingReview() throws Exception {
        Long faculty = accounts.findByLoginId("faculty2").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS250' and lms.status = 'DRAFT'
                """, Long.class);

        for (int round = 0; round < 3; round++) {
            String edited = "Edited " + round;
            Long questionId = service.add(faculty, lmsCourseId, QuestionFixtures.singleChoice("Round " + round), null).getId();
            service.markReviewed(faculty, questionId);

            Concurrently.run(
                    () -> service.edit(faculty, questionId, QuestionFixtures.singleChoice(edited), null),
                    () -> service.allowPractice(faculty, questionId, true));

            Map<String, Object> row = jdbc.queryForMap(
                    "select question_text, review_status, practice_allowed from lms_questions where id = ?", questionId);
            assertThat(row.get("question_text")).isEqualTo(edited);
            assertThat(row.get("review_status")).isEqualTo("NEEDS_REVIEW");
            assertThat(row.get("practice_allowed")).isIn(false, 0, 0L);
            assertThat(jdbc.queryForList("select option_text from lms_question_options where lms_question_id = ? order by position",
                    String.class, questionId)).isEqualTo(List.of("First normal form", "Second normal form"));
        }

        List<LmsQuestion> bank = service.list(faculty, lmsCourseId);
        assertThat(bank).extracting(LmsQuestion::getText).containsExactly("Edited 2", "Edited 1", "Edited 0");
        assertThat(bank).allSatisfy(question -> assertThat(question.getOptions())
                .extracting(LmsQuestionOption::getText).containsExactly("First normal form", "Second normal form"));
    }
}
