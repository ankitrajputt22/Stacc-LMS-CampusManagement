package com.stacc.backend.academic.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.Concurrently.Outcome;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationResult;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * An offering's capacity under truly simultaneous self-registrations on MySQL: two students registering at once for an
 * offering with one place leave exactly one enrolled. Uses its own course and offering in the demo semester, whose
 * registration period the demo data opens, and withdraws the winner afterwards.
 */
class RegistrationCapacityMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private StudentRegistrationService registration;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theLastSeatIsTakenOnlyOnce() throws Exception {
        List<Long> accounts = jdbc.queryForList(
                "select id from user_accounts where login_id in ('student1', 'student2') order by login_id", Long.class);
        assertThat(accounts).hasSize(2);
        Long semesterId = jdbc.queryForObject("""
                select enrollment.semester_id from semester_enrollments enrollment
                join student_profiles profile on profile.id = enrollment.student_profile_id
                join user_accounts account on account.id = profile.user_account_id
                where account.login_id = 'student1' and enrollment.status = 'ENROLLED'
                """, Long.class);
        Long departmentId = jdbc.queryForObject("select id from departments where code = 'CSE'", Long.class);
        jdbc.update("insert into courses (department_id, code, name, credits, course_type, status, created_at, updated_at) "
                + "values (?, 'ITREG1', 'Registration race', 1.00, 'ELECTIVE', 'ACTIVE', now(6), now(6))", departmentId);
        Long courseId = jdbc.queryForObject("select id from courses where code = 'ITREG1'", Long.class);
        jdbc.update("insert into course_offerings (course_id, semester_id, capacity, registration_status, status, "
                + "created_at, updated_at) values (?, ?, 1, 'OPEN', 'ACTIVE', now(6), now(6))", courseId, semesterId);
        Long offeringId = jdbc.queryForObject("select id from course_offerings where course_id = ?", Long.class, courseId);
        try {
            List<Outcome<RegistrationResult>> outcomes = Concurrently.run(
                    () -> registration.register(accounts.get(0), List.of(offeringId)),
                    () -> registration.register(accounts.get(1), List.of(offeringId)));

            assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome -> {
                assertThat(outcome.failure()).isInstanceOf(ApiException.class);
                assertThat(((ApiException) outcome.failure()).getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(outcome.failure().getMessage()).isEqualTo("ITREG1 is full: its one place is taken.");
            });
            assertThat(jdbc.queryForObject(
                    "select count(*) from course_enrollments where course_offering_id = ? and status = 'ENROLLED'",
                    Integer.class, offeringId)).isEqualTo(1);
        } finally {
            jdbc.update("update course_enrollments set status = 'WITHDRAWN' where course_offering_id = ?", offeringId);
            jdbc.update("update course_offerings set registration_status = 'CLOSED', status = 'CLOSED' where id = ?",
                    offeringId);
        }
    }
}
