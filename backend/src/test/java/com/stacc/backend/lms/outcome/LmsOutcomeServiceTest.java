package com.stacc.backend.lms.outcome;

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
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class LmsOutcomeServiceTest {

    private static final Long FACULTY = 21L;
    private static final Long STUDENT = 12L;
    private static final Long COURSE_ID = 7L;

    private final LmsCourseOutcomeRepository outcomes = mock(LmsCourseOutcomeRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsOutcomeService service = new LmsOutcomeService(outcomes, lmsCourses, studentAccess, facultyAccess);
    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE_ID);

    private LmsCourseOutcome outcome(long id, int number) {
        LmsCourseOutcome created = new LmsCourseOutcome(course, number, "Outcome " + number);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    @BeforeEach
    void setUp() {
        when(facultyAccess.canAccessCourse(FACULTY, COURSE_ID)).thenReturn(true);
        when(studentAccess.canAccessCourse(STUDENT, COURSE_ID)).thenReturn(true);
        when(lmsCourses.findByIdForUpdate(COURSE_ID)).thenReturn(Optional.of(course));
        when(outcomes.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void aNewOutcomeTakesTheNextNumberEvenAfterAnArchivedOne() {
        LmsCourseOutcome archived = outcome(92, 2);
        archived.archive();
        when(outcomes.findAllByLmsCourseIdForUpdate(COURSE_ID)).thenReturn(List.of(outcome(91, 1), archived));

        LmsCourseOutcome added = service.add(FACULTY, COURSE_ID, "  Design efficient algorithms.  ");

        assertThat(added.getCode()).isEqualTo("CO3");
        assertThat(added.getDescription()).isEqualTo("Design efficient algorithms.");
        assertThat(added.isActive()).isTrue();
    }

    @Test
    void anArchivedOutcomeCannotBeCorrectedAndArchivingAgainChangesNothing() {
        LmsCourseOutcome archived = outcome(92, 2);
        archived.archive();
        when(outcomes.findWithCourseById(92L)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.describe(FACULTY, 92L, "New")).isInstanceOfSatisfying(ApiException.class,
                refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(service.archive(FACULTY, 92L).getStatus()).isEqualTo(LmsOutcomeStatus.ARCHIVED);
    }

    @Test
    void studentsReadOnlyActiveOutcomesAndOthersGetTheSameNotFound() {
        service.forStudent(STUDENT, COURSE_ID);
        verify(outcomes).findAllByLmsCourseIdAndStatusOrderByNumberAsc(COURSE_ID, LmsOutcomeStatus.ACTIVE);

        when(outcomes.findWithCourseById(91L)).thenReturn(Optional.of(outcome(91, 1)));
        for (Runnable call : new Runnable[] {
            () -> service.forStudent(99L, COURSE_ID),
            () -> service.forFaculty(99L, COURSE_ID),
            () -> service.add(99L, COURSE_ID, "Text"),
            () -> service.describe(99L, 91L, "Text"),
            () -> service.archive(99L, 91L),
            () -> service.archive(FACULTY, 999L)}) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                    refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        verify(outcomes, never()).saveAndFlush(any());
    }
}
