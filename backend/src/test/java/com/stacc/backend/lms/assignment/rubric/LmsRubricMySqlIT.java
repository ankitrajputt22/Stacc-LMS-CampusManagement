package com.stacc.backend.lms.assignment.rubric;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.assignment.LmsAssignmentManagementService;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentGradingService;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Editing a rubric and grading with it at the same moment never leave a grade pointing at criteria that were replaced:
 * either the grade comes first and the edit is refused, or the edit comes first and the grade, naming the old criteria,
 * is refused. Uses its own rubric and draft-then-published assignment in CS301, handed in by studentfaculty1.
 */
class LmsRubricMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsRubricService rubrics;

    @Autowired
    private LmsAssignmentManagementService assignments;

    @Autowired
    private LmsAssignmentSubmissionService submissions;

    @Autowired
    private LmsAssignmentGradingService grading;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anEditAndAGradeWithTheRubricAtOnceNeverBothSucceed() throws Exception {
        Long faculty = accounts.findByLoginId("faculty1").orElseThrow().getId();
        Long student = accounts.findByLoginId("studentfaculty1").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS301' and lms.status = 'PUBLISHED'
                """, Long.class);
        LmsRubric rubric = rubrics.create(faculty, lmsCourseId, RubricFixtures.content("Race rubric", "12", "8"));
        Long assignmentId = assignments.create(faculty, lmsCourseId, "Rubric race", "Hand in anything.",
                java.time.Instant.parse("2027-01-10T10:00:00Z"), new BigDecimal("20")).getId();
        assignments.useRubric(faculty, assignmentId, rubric.getId());
        assignments.publish(faculty, assignmentId);
        Long attemptId = submissions.submit(student, lmsCourseId, assignmentId, "My work.", List.of()).attempt().getId();
        List<Long> criteria = rubric.getCriteria().stream().map(LmsRubricCriterion::getId).toList();

        List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                () -> grading.gradeWithRubric(faculty, attemptId, List.of(
                        new LmsAssignmentGradingService.RubricScoreInput(criteria.get(0), new BigDecimal("10"), null),
                        new LmsAssignmentGradingService.RubricScoreInput(criteria.get(1), new BigDecimal("7"), null)), null),
                () -> rubrics.edit(faculty, rubric.getId(), RubricFixtures.content("Race rubric edited", "15", "5")));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.failure()).isInstanceOf(ApiException.class));
        Integer scores = jdbc.queryForObject("""
                select count(*) from lms_rubric_scores score join lms_rubric_criteria criterion on criterion.id = score.lms_rubric_criterion_id
                where criterion.lms_rubric_id = ?
                """, Integer.class, rubric.getId());
        if (outcomes.get(0).succeeded()) {
            assertThat(scores).isEqualTo(2);
            assertThat(jdbc.queryForObject("select title from lms_rubrics where id = ?", String.class, rubric.getId())).isEqualTo("Race rubric");
        } else {
            assertThat(scores).isZero();
        }
    }
}
