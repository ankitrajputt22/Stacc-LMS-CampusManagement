package com.stacc.backend.lms.ai.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AiTeacherDocumentMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private AiTeacherDocumentService service;

    @Autowired
    private AiTeacherDocumentRepository documents;

    @Autowired
    private LmsCourseRepository courses;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private LmsFacultyAccessService facultyAccess;

    @Test
    void theFiftiethPlaceIsTakenOnce() throws Exception {
        Long faculty = accounts.findByLoginId("emp1021").orElseThrow().getId();
        Long course = courses.findAll().stream().map(LmsCourse::getId)
                .filter(id -> facultyAccess.canAccessCourse(faculty, id)).reduce((first, second) -> second).orElseThrow();
        long existing = documents.countByLmsCourseIdAndStatus(course, AiTeacherDocument.Status.ACTIVE);
        for (long i = existing; i < AiTeacherDocumentService.MAX_ACTIVE_DOCUMENTS - 1; i++) {
            service.create(faculty, course, "Notes " + i, "Text " + i);
        }

        List<Concurrently.Outcome<Long>> outcomes = Concurrently.run(
                () -> service.create(faculty, course, "First", "Text").getId(),
                () -> service.create(faculty, course, "Second", "Text").getId());

        assertThat(outcomes.stream().filter(outcome -> outcome.result() != null)).hasSize(1);
        assertThat(outcomes.stream().map(Concurrently.Outcome::failure).filter(ApiException.class::isInstance)).hasSize(1);
        assertThat(documents.countByLmsCourseIdAndStatus(course, AiTeacherDocument.Status.ACTIVE))
                .isEqualTo(AiTeacherDocumentService.MAX_ACTIVE_DOCUMENTS);
    }
}
