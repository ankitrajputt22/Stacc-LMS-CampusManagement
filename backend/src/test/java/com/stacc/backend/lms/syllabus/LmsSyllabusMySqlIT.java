package com.stacc.backend.lms.syllabus;

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
 * Two first saves of one course's syllabus at the same moment make one syllabus, because both lock the course row.
 * Uses CS302's draft LMS course and faculty2, whose syllabus no other test touches.
 */
class LmsSyllabusMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsSyllabusService service;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoFirstSavesAtOnceMakeOneSyllabus() throws Exception {
        Long faculty = accounts.findByLoginId("faculty2").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS302'
                """, Long.class);

        List<Outcome<LmsSyllabus>> outcomes = Concurrently.run(
                () -> service.saveDraft(faculty, lmsCourseId, "## First draft"),
                () -> service.saveDraft(faculty, lmsCourseId, "## Second draft"));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(jdbc.queryForObject("select count(*) from lms_syllabi where lms_course_id = ?", Integer.class, lmsCourseId))
                .isEqualTo(1);

        service.publish(faculty, lmsCourseId);
        assertThat(jdbc.queryForObject("select published_content is not null from lms_syllabi where lms_course_id = ?",
                Boolean.class, lmsCourseId)).isTrue();
    }
}
