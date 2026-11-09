package com.stacc.backend.lms.content;

import static com.stacc.backend.lms.content.ContentFixtures.module;
import static com.stacc.backend.lms.content.ContentFixtures.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LmsPageCompletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");
    private static final Long STUDENT = 12L;
    private static final Long COURSE_ID = 7L;

    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final LmsTopicRepository topics = mock(LmsTopicRepository.class);
    private final LmsPageCompletionRepository completions = mock(LmsPageCompletionRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final LmsLearningPathService paths = mock(LmsLearningPathService.class);
    private final LmsPageCompletionService service = new LmsPageCompletionService(studentAccess, topics, completions,
            courseEnrollments, paths, Clock.fixed(NOW, ZoneOffset.UTC));

    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE_ID);
    private final LmsModule first = module(course, 11, 1, LmsContentStatus.PUBLISHED);
    private final LmsModule second = module(course, 12, 2, LmsContentStatus.PUBLISHED);
    private final LmsTopic page = topic(first, 31, 1, LmsContentStatus.PUBLISHED);
    private final LmsTopic lockedPage = topic(second, 41, 1, LmsContentStatus.PUBLISHED);
    private final CourseEnrollment enrollment = mock(CourseEnrollment.class);

    @BeforeEach
    void setUp() {
        page.markRequired(true);
        second.requirePrevious(true);
        when(enrollment.getId()).thenReturn(500L);
        when(enrollment.getStatus()).thenReturn(CourseEnrollmentStatus.ENROLLED);
        when(studentAccess.findAccessGrantingEnrollment(STUDENT, COURSE_ID)).thenReturn(Optional.of(enrollment));
        when(courseEnrollments.findByIdForUpdate(500L)).thenReturn(Optional.of(enrollment));
        when(topics.findWithModuleById(31L)).thenReturn(Optional.of(page));
        when(topics.findWithModuleById(41L)).thenReturn(Optional.of(lockedPage));
        when(paths.pathFor(enrollment, COURSE_ID)).thenReturn(LmsLearningPath.of(List.of(first, second), List.of(page, lockedPage), Set.of()));
        when(completions.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void aPageIsCompletedOnceAndMarkingItAgainChangesNothing() {
        when(completions.findForUpdate(500L, 31L)).thenReturn(Optional.empty());
        LmsPageCompletion recorded = service.complete(STUDENT, COURSE_ID, 31L);
        assertThat(recorded.getCompletedAt()).isEqualTo(NOW);

        when(completions.findForUpdate(500L, 31L)).thenReturn(Optional.of(recorded));
        assertThat(service.complete(STUDENT, COURSE_ID, 31L)).isSameAs(recorded);
        verify(completions).save(any());
    }

    @Test
    void aPageInALockedModuleCannotBeCompleted() {
        assertThatThrownBy(() -> service.complete(STUDENT, COURSE_ID, 41L)).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(refusal.getMessage()).isEqualTo("This page is locked. Complete “Module 11” first.");
        });
        verify(completions, never()).save(any());
    }

    @Test
    void aHiddenForeignOrMissingPageAndACourseTheStudentMayNotUseLookTheSame() {
        LmsTopic draft = topic(first, 32, 2, LmsContentStatus.DRAFT);
        when(topics.findWithModuleById(32L)).thenReturn(Optional.of(draft));
        LmsTopic foreign = topic(module(ContentFixtures.lmsCourse(8), 21, 1, LmsContentStatus.PUBLISHED), 51, 1,
                LmsContentStatus.PUBLISHED);
        when(topics.findWithModuleById(51L)).thenReturn(Optional.of(foreign));

        for (Long pageId : List.of(32L, 51L, 999L)) {
            assertThatThrownBy(() -> service.complete(STUDENT, COURSE_ID, pageId)).isInstanceOfSatisfying(ApiException.class,
                    refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        assertThatThrownBy(() -> service.complete(99L, COURSE_ID, 31L)).isInstanceOfSatisfying(ApiException.class,
                refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(completions, never()).save(any());
    }

    @Test
    void anEnrollmentThatEndedMeanwhileRecordsNothing() {
        CourseEnrollment ended = mock(CourseEnrollment.class);
        when(ended.getStatus()).thenReturn(CourseEnrollmentStatus.DROPPED);
        when(courseEnrollments.findByIdForUpdate(500L)).thenReturn(Optional.of(ended));

        assertThatThrownBy(() -> service.complete(STUDENT, COURSE_ID, 31L)).isInstanceOf(ApiException.class);
        verify(completions, never()).save(any());
    }
}
