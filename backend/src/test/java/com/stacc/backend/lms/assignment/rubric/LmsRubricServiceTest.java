package com.stacc.backend.lms.assignment.rubric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.assignment.submission.SubmissionFixtures;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LmsRubricServiceTest {

    private static final Long FACULTY = 21L;
    private static final Long OTHER = 22L;
    private static final Long COURSE_ID = 7L;

    private final LmsRubricRepository rubrics = mock(LmsRubricRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsRubricService service = new LmsRubricService(rubrics, lmsCourses, facultyAccess);
    private final LmsCourse course = SubmissionFixtures.course(COURSE_ID);
    private LmsRubric stored;

    private static void refusedWith(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    @BeforeEach
    void setUp() {
        stored = RubricFixtures.rubric(course, 4, "10", "10");
        when(facultyAccess.canAccessCourse(FACULTY, COURSE_ID)).thenReturn(true);
        when(lmsCourses.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(rubrics.findLmsCourseIdById(4L)).thenReturn(Optional.of(COURSE_ID));
        when(rubrics.findByIdForUpdate(4L)).thenReturn(Optional.of(stored));
        when(rubrics.findUseForShare(4L)).thenReturn(List.of());
        when(rubrics.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void aRubricIsAddedAndEditedWhileUnused() {
        assertThat(service.create(FACULTY, COURSE_ID, RubricFixtures.content("New", "5", "5")).getTotalPoints()).isEqualByComparingTo("10");
        assertThat(service.edit(FACULTY, 4L, RubricFixtures.content("Edited", "15", "5")).getTitle()).isEqualTo("Edited");
        refusedWith(() -> service.create(FACULTY, COURSE_ID, new RubricContent("Empty", null, List.of())), HttpStatus.BAD_REQUEST,
                "A rubric needs 1 to 20 criteria.");
    }

    @Test
    void aRubricUsedForGradingOrArchivedKeepsItsCriteria() {
        when(rubrics.findUseForShare(4L)).thenReturn(List.of(99L));
        refusedWith(() -> service.edit(FACULTY, 4L, RubricFixtures.content("Edited", "15", "5")), HttpStatus.CONFLICT,
                LmsRubricService.RUBRIC_IN_USE);

        service.archive(FACULTY, 4L);
        refusedWith(() -> service.edit(FACULTY, 4L, RubricFixtures.content("Edited", "15", "5")), HttpStatus.CONFLICT,
                LmsRubricService.RUBRIC_ARCHIVED);
    }

    @Test
    void someoneWhoDoesNotTeachTheCourseSeesNoRubric() {
        refusedWith(() -> service.list(OTHER, COURSE_ID), HttpStatus.NOT_FOUND, LmsRubricService.COURSE_UNAVAILABLE);
        refusedWith(() -> service.archive(OTHER, 4L), HttpStatus.NOT_FOUND, LmsRubricService.RUBRIC_UNAVAILABLE);
        verify(rubrics, never()).findByIdForUpdate(any());
    }
}
