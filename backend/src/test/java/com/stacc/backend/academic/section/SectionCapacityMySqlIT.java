package com.stacc.backend.academic.section;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.Concurrently.Outcome;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.academic.admin.AcademicEnrollmentManagementService;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentSectionChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.SemesterEnrollmentResponse;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A section's capacity under truly simultaneous placements on MySQL: two students placed at once into a section with one
 * free place leave exactly one in it. Uses its own section in the demo semester and puts the two demo students back in
 * their seeded sections afterwards.
 */
class SectionCapacityMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private AcademicEnrollmentManagementService enrollments;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theLastPlaceIsTakenOnlyOnce() throws Exception {
        List<Long> ids = jdbc.queryForList("""
                select enrollment.id from semester_enrollments enrollment
                join student_profiles profile on profile.id = enrollment.student_profile_id
                join user_accounts account on account.id = profile.user_account_id
                where account.login_id in ('student1', 'student2') and enrollment.section_id is not null
                order by account.login_id
                """, Long.class);
        assertThat(ids).hasSize(2);
        List<Long> original = ids.stream()
                .map(id -> jdbc.queryForObject("select section_id from semester_enrollments where id = ?", Long.class, id))
                .toList();
        Long semesterId = jdbc.queryForObject("select semester_id from semester_enrollments where id = ?", Long.class, ids.get(0));
        jdbc.update("insert into sections (semester_id, code, capacity, status, created_at, updated_at) "
                + "values (?, 'ITCAP', 1, 'ACTIVE', now(6), now(6))", semesterId);
        Long single = jdbc.queryForObject("select id from sections where semester_id = ? and code = 'ITCAP'", Long.class, semesterId);
        try {
            List<Outcome<SemesterEnrollmentResponse>> outcomes = Concurrently.run(
                    () -> enrollments.changeSection(ids.get(0), new SemesterEnrollmentSectionChange(single)),
                    () -> enrollments.changeSection(ids.get(1), new SemesterEnrollmentSectionChange(single)));

            assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome -> {
                assertThat(outcome.failure()).isInstanceOf(ApiException.class);
                assertThat(((ApiException) outcome.failure()).getStatus()).isEqualTo(HttpStatus.CONFLICT);
            });
            assertThat(jdbc.queryForObject("select count(*) from semester_enrollments where section_id = ?", Integer.class,
                    single)).isEqualTo(1);
        } finally {
            for (int i = 0; i < ids.size(); i++) {
                enrollments.changeSection(ids.get(i), new SemesterEnrollmentSectionChange(original.get(i)));
            }
            jdbc.update("update sections set status = 'INACTIVE' where id = ?", single);
        }
    }
}
