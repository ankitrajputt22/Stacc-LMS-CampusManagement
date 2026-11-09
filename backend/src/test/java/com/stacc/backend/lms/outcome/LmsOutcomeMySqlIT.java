package com.stacc.backend.lms.outcome;

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
 * Two outcomes added at the same moment get different codes: both lock the course row, then read the outcomes with a
 * locking read. Uses CS302's draft LMS course and faculty2, whose outcomes no other test touches.
 */
class LmsOutcomeMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsOutcomeService service;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoOutcomesAddedAtOnceGetTheirOwnCodes() throws Exception {
        Long faculty = accounts.findByLoginId("faculty2").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS302'
                """, Long.class);

        List<Outcome<LmsCourseOutcome>> outcomes = Concurrently.run(
                () -> service.add(faculty, lmsCourseId, "Model data as relations."),
                () -> service.add(faculty, lmsCourseId, "Write queries in SQL."));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(jdbc.queryForList("select code from lms_course_outcomes where lms_course_id = ? order by code",
                String.class, lmsCourseId)).containsExactly("CO1", "CO2");
    }
}
