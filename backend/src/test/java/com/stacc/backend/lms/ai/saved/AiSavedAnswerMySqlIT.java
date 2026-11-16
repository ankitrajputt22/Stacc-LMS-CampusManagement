package com.stacc.backend.lms.ai.saved;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Two tabs saving a student's 50th answer at once: the account row lock lets exactly one through; deleting is for good. */
class AiSavedAnswerMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private AiSavedAnswerService service;

    @Autowired
    private AiSavedAnswerRepository answers;

    @Autowired
    private LmsCourseRepository courses;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private LmsStudentAccessService studentAccess;

    @Test
    void theFiftiethPlaceIsTakenOnceAndDeletingRemovesTheRow() throws Exception {
        Long student = accounts.findByLoginId("student1").orElseThrow().getId();
        // The first course student1 may use; the notes test uses the last one, and no other test saves answers.
        Long course = courses.findAll().stream().map(LmsCourse::getId)
                .filter(id -> studentAccess.canAccessCourse(student, id)).findFirst().orElseThrow();
        for (long i = answers.countByLmsCourseIdAndUserAccountId(course, student); i < AiSavedAnswerService.MAX_SAVED - 1; i++) {
            service.save(student, course, "Question " + i, "Answer " + i, List.of());
        }

        List<Concurrently.Outcome<Long>> outcomes = Concurrently.run(
                () -> service.save(student, course, "First", "Answer", List.of()).getId(),
                () -> service.save(student, course, "Second", "Answer", List.of()).getId());

        assertThat(outcomes.stream().filter(outcome -> outcome.result() != null)).hasSize(1);
        assertThat(outcomes.stream().map(Concurrently.Outcome::failure).filter(ApiException.class::isInstance)).hasSize(1);
        assertThat(answers.countByLmsCourseIdAndUserAccountId(course, student)).isEqualTo(AiSavedAnswerService.MAX_SAVED);

        Long saved = outcomes.stream().map(Concurrently.Outcome::result).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        service.delete(student, course, saved);
        assertThat(answers.findById(saved)).isEmpty();
    }
}
