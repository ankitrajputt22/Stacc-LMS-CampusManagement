package com.stacc.backend.lms.gradebook;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Publishing the same column twice at once makes one grade item, and entering a student's marks twice at once leaves one
 * entry. Uses its own manual item and the gradebook column of CS301's published "Linked list implementation", which no
 * other test publishes.
 */
class LmsGradebookMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsGradebookService gradebook;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void simultaneousPublicationsAndMarksLeaveOneRowEach() throws Exception {
        Long faculty = accounts.findByLoginId("faculty1").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS301' and lms.status = 'PUBLISHED'
                """, Long.class);
        Long assignmentId = jdbc.queryForObject(
                "select id from lms_assignments where lms_course_id = ? and title = 'Linked list implementation'", Long.class, lmsCourseId);

        List<Concurrently.Outcome<LmsGradeItem>> publications = Concurrently.run(
                () -> gradebook.publishColumn(faculty, lmsCourseId, GradeItemType.ASSIGNMENT, assignmentId),
                () -> gradebook.publishColumn(faculty, lmsCourseId, GradeItemType.ASSIGNMENT, assignmentId));
        assertThat(publications).allMatch(Concurrently.Outcome::succeeded);
        assertThat(jdbc.queryForObject("select count(*) from lms_grade_items where lms_assignment_id = ?", Integer.class, assignmentId))
                .isEqualTo(1);

        Long itemId = gradebook.addManualItem(faculty, lmsCourseId, "Concurrent test", new BigDecimal("10")).getId();
        Long enrollmentId = gradebook.gradebook(faculty, lmsCourseId).students().getFirst().getId();
        List<Concurrently.Outcome<LmsGradeEntry>> marks = Concurrently.run(
                () -> gradebook.enterMarks(faculty, itemId, enrollmentId, new BigDecimal("6"), null),
                () -> gradebook.enterMarks(faculty, itemId, enrollmentId, new BigDecimal("7"), null));
        assertThat(marks).allMatch(Concurrently.Outcome::succeeded);
        assertThat(jdbc.queryForList("select marks from lms_grade_entries where lms_grade_item_id = ?", BigDecimal.class, itemId))
                .singleElement().satisfies(value -> assertThat(value).isIn(new BigDecimal("6.00"), new BigDecimal("7.00")));
    }
}
