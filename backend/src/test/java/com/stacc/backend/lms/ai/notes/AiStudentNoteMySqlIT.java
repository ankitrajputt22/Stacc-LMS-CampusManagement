package com.stacc.backend.lms.ai.notes;

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

/** Two tabs adding a student's 20th note at once: the account row lock lets exactly one through. */
class AiStudentNoteMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private AiStudentNoteService service;

    @Autowired
    private AiStudentNoteRepository notes;

    @Autowired
    private LmsCourseRepository courses;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private LmsStudentAccessService studentAccess;

    @Test
    void theTwentiethPlaceIsTakenOnce() throws Exception {
        Long student = accounts.findByLoginId("student1").orElseThrow().getId();
        // The last course student1 may use; no other test writes notes.
        Long course = courses.findAll().stream().map(LmsCourse::getId)
                .filter(id -> studentAccess.canAccessCourse(student, id)).reduce((first, second) -> second).orElseThrow();
        for (long i = notes.countByLmsCourseIdAndUserAccountId(course, student); i < AiStudentNoteService.MAX_NOTES - 1; i++) {
            service.create(student, course, "Note " + i, "Text " + i);
        }

        List<Concurrently.Outcome<Long>> outcomes = Concurrently.run(
                () -> service.create(student, course, "First", "Text").getId(),
                () -> service.create(student, course, "Second", "Text").getId());

        assertThat(outcomes.stream().filter(outcome -> outcome.result() != null)).hasSize(1);
        assertThat(outcomes.stream().map(Concurrently.Outcome::failure).filter(ApiException.class::isInstance)).hasSize(1);
        assertThat(notes.countByLmsCourseIdAndUserAccountId(course, student)).isEqualTo(AiStudentNoteService.MAX_NOTES);
    }
}
