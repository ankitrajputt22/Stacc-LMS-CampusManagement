package com.stacc.backend.lms.assignment.submission;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.assignment.LmsAssignmentManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Several hand-ins at once against a one-attempt assignment make exactly one attempt; the others are refused, because
 * the limit is counted under the course enrollment row lock. Uses its own assignment in CS301, handed in by
 * studentfaculty1.
 */
class SubmissionAttemptLimitMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsAssignmentSubmissionService submissions;

    @Autowired
    private LmsAssignmentManagementService assignments;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void simultaneousHandInsNeverPassTheAttemptLimit() throws Exception {
        Long faculty = accounts.findByLoginId("faculty1").orElseThrow().getId();
        Long student = accounts.findByLoginId("studentfaculty1").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS301' and lms.status = 'PUBLISHED'
                """, Long.class);
        Long assignmentId = assignments.create(faculty, lmsCourseId, "One attempt only", "Hand in once.",
                Instant.parse("2099-01-10T10:00:00Z"), new BigDecimal("10")).getId();
        assignments.changeSubmissionRules(faculty, assignmentId, null, null, true, 1);
        assignments.publish(faculty, assignmentId);

        List<Concurrently.Outcome<LmsAssignmentSubmissionService.SubmittedAttempt>> outcomes = Concurrently.run(
                () -> submissions.submit(student, lmsCourseId, assignmentId, "First", List.of()),
                () -> submissions.submit(student, lmsCourseId, assignmentId, "Second", List.of()),
                () -> submissions.submit(student, lmsCourseId, assignmentId, "Third", List.of()));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).allSatisfy(outcome ->
                assertThat(outcome.failure()).isInstanceOfSatisfying(ApiException.class,
                        refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.CONFLICT)));
        assertThat(submissions.history(student, lmsCourseId, assignmentId)).hasSize(1);
    }
}
