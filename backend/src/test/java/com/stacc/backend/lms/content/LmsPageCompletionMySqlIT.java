package com.stacc.backend.lms.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.Concurrently.Outcome;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two "Mark as complete" clicks at the same moment record one completion: both lock the course enrollment row and then
 * read the completion with a locking read. Uses studentfaculty1 and CS301's "Arrays and memory layout", whose
 * completions no other test touches.
 */
class LmsPageCompletionMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsPageCompletionService service;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoCompletionsAtOnceRecordOne() throws Exception {
        Long student = accounts.findByLoginId("studentfaculty1").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS301'
                """, Long.class);
        Long pageId = jdbc.queryForObject("""
                select topic.id from lms_topics topic join lms_modules module on module.id = topic.lms_module_id
                where module.lms_course_id = ? and topic.title = 'Arrays and memory layout'
                """, Long.class, lmsCourseId);

        List<Outcome<LmsPageCompletion>> outcomes = Concurrently.run(
                () -> service.complete(student, lmsCourseId, pageId),
                () -> service.complete(student, lmsCourseId, pageId));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(jdbc.queryForObject("select count(*) from lms_page_completions where lms_topic_id = ?", Integer.class, pageId))
                .isEqualTo(1);
    }
}
