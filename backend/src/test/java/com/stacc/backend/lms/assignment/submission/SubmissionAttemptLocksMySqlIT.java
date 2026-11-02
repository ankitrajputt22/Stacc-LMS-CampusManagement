package com.stacc.backend.lms.assignment.submission;

import static org.assertj.core.api.Assertions.assertThat;

import com.stacc.backend.Concurrently;
import com.stacc.backend.Concurrently.Outcome;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionService.SubmittedAttempt;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Several hand-ins by one student at the same moment, on MySQL: each becomes its own attempt with its own number, and
 * the numbers continue without gaps or repeats. The numbers are reserved under the course enrollment row lock, never from
 * {@code MAX + 1}. Uses student2 in CS301's published assignment with a future deadline, which no other MySQL test uses.
 */
class SubmissionAttemptLocksMySqlIT extends MySqlIntegrationTest {

    private static final int HAND_INS = 5;

    @Autowired
    private LmsAssignmentSubmissionService submissions;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void handInsAtTheSameMomentGetTheirOwnAttemptNumbers() throws Exception {
        Long student = accounts.findByLoginId("student2").orElseThrow().getId();
        Map<String, Object> assignment = jdbc.queryForMap("""
                select assignment.id, assignment.lms_course_id from lms_assignments assignment
                join lms_courses course on course.id = assignment.lms_course_id
                join course_offerings offering on offering.id = course.course_offering_id
                join courses catalog on catalog.id = offering.course_id
                where catalog.code = 'CS301' and assignment.status = 'PUBLISHED' and assignment.due_at > utc_timestamp(6)
                order by assignment.id limit 1
                """);
        Long assignmentId = ((Number) assignment.get("id")).longValue();
        Long courseId = ((Number) assignment.get("lms_course_id")).longValue();
        int before = attemptsSoFar(student, courseId, assignmentId);

        @SuppressWarnings("unchecked")
        List<Outcome<SubmittedAttempt>> outcomes = Concurrently.run(IntStream.range(0, HAND_INS)
                .mapToObj(index -> (java.util.concurrent.Callable<SubmittedAttempt>) () ->
                        submissions.submit(student, courseId, assignmentId, "Hand-in " + index, List.of()))
                .toArray(java.util.concurrent.Callable[]::new));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        assertThat(outcomes.stream().map(outcome -> outcome.result().attempt().getAttemptNumber()))
                .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(before + 1, before + HAND_INS).boxed().toList());
        assertThat(attemptsSoFar(student, courseId, assignmentId)).isEqualTo(before + HAND_INS);
    }

    private int attemptsSoFar(Long student, Long courseId, Long assignmentId) {
        return submissions.history(student, courseId, assignmentId).size();
    }
}
