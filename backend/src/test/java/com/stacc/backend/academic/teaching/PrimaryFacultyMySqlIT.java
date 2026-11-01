package com.stacc.backend.academic.teaching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.Concurrently.Outcome;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.academic.admin.FacultyAssignmentRequests.FacultyAssignmentCreate;
import com.stacc.backend.academic.admin.FacultyAssignmentService;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.OfferingFaculty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * One primary faculty member per offering under truly simultaneous requests on MySQL: two admins assigning different
 * primaries at once leave exactly one. Uses the demo CS401 offering, which no other MySQL test touches, and makes the
 * assignments it created inactive afterwards.
 */
class PrimaryFacultyMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private FacultyAssignmentService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anOfferingNeverGetsTwoPrimaries() throws Exception {
        Long offering = jdbc.queryForObject("""
                select offering.id from course_offerings offering join courses course on course.id = offering.course_id
                where course.code = 'CS401'
                """, Long.class);
        jdbc.update("update teaching_assignments set status = 'INACTIVE' where course_offering_id = ?", offering);
        List<Long> faculty = jdbc.queryForList("""
                select profile.id from faculty_profiles profile join user_accounts account on account.id = profile.user_account_id
                where account.login_id in ('faculty1', 'faculty2') order by account.login_id
                """, Long.class);

        List<Outcome<OfferingFaculty>> outcomes = Concurrently.run(
                () -> service.assign(new FacultyAssignmentCreate(offering, faculty.get(0), TeachingRole.PRIMARY)),
                () -> service.assign(new FacultyAssignmentCreate(offering, faculty.get(1), TeachingRole.PRIMARY)));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from teaching_assignments
                where course_offering_id = ? and status = 'ACTIVE' and assignment_role = 'PRIMARY'
                """, Integer.class, offering)).isEqualTo(1);
    }
}
