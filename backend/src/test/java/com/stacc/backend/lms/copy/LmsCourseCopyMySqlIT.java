package com.stacc.backend.lms.copy;

import static org.assertj.core.api.Assertions.assertThat;

import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * On MySQL with the demo data: faculty2 sees last session's archived CS250 as a copy source for this year's draft CS250
 * and copies its module, pages, and syllabus as drafts. CS250 is used by no other test.
 */
class LmsCourseCopyMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsCourseCopyService service;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void lastSessionsArchivedCourseIsCopiedAsDrafts() {
        Long faculty2 = accounts.findByLoginId("faculty2").orElseThrow().getId();
        Long target = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS250' and lms.status = 'DRAFT'
                """, Long.class);

        var sources = service.sources(faculty2, target);
        assertThat(sources).singleElement().extracting(LmsCourse::getStatus).hasToString("ARCHIVED");
        assertThat(service.sources(accounts.findByLoginId("faculty1").orElseThrow().getId(), target)).isEmpty();

        LmsCourseCopyService.CopyResult result = service.copy(faculty2, target, sources.getFirst().getId());

        assertThat(result).isEqualTo(new LmsCourseCopyService.CopyResult(1, 2, 0, true));
        assertThat(jdbc.queryForObject("""
                select count(*) from lms_topics topic join lms_modules module on module.id = topic.lms_module_id
                where module.lms_course_id = ? and topic.status = 'DRAFT' and module.status = 'DRAFT'
                """, Integer.class, target)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select published_content is null from lms_syllabi where lms_course_id = ?",
                Boolean.class, target)).isTrue();
    }
}
