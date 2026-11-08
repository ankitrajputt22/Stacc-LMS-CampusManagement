package com.stacc.backend.lms.syllabus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

class LmsSyllabusServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");
    private static final Long FACULTY = 21L;
    private static final Long STUDENT = 12L;
    private static final Long COURSE_ID = 7L;

    private final LmsSyllabusRepository syllabi = mock(LmsSyllabusRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsSyllabusService service = new LmsSyllabusService(syllabi, lmsCourses, studentAccess, facultyAccess,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE_ID);

    @BeforeEach
    void setUp() {
        when(facultyAccess.canAccessCourse(FACULTY, COURSE_ID)).thenReturn(true);
        when(studentAccess.canAccessCourse(STUDENT, COURSE_ID)).thenReturn(true);
        when(lmsCourses.findByIdForUpdate(COURSE_ID)).thenReturn(Optional.of(course));
        when(syllabi.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void theFirstSaveStartsADraftThatStudentsDoNotSee() {
        when(syllabi.findByLmsCourseId(COURSE_ID)).thenReturn(Optional.empty());

        LmsSyllabus saved = service.saveDraft(FACULTY, COURSE_ID, "  ## Overview  ");

        assertThat(saved.getDraftContent()).isEqualTo("## Overview");
        assertThat(saved.isPublished()).isFalse();
        assertThat(saved.hasUnpublishedChanges()).isTrue();
        when(syllabi.findByLmsCourseId(COURSE_ID)).thenReturn(Optional.of(saved));
        assertThat(service.forStudent(STUDENT, COURSE_ID)).isEmpty();
    }

    @Test
    void publishingCopiesTheDraftAndALaterDraftDoesNotChangeWhatStudentsRead() {
        LmsSyllabus syllabus = new LmsSyllabus(course, "First", NOW.minusSeconds(60));
        when(syllabi.findByLmsCourseId(COURSE_ID)).thenReturn(Optional.of(syllabus));

        service.publish(FACULTY, COURSE_ID);
        assertThat(syllabus.getPublishedContent()).isEqualTo("First");
        assertThat(syllabus.getFirstPublishedAt()).isEqualTo(NOW);

        service.saveDraft(FACULTY, COURSE_ID, "Second");
        assertThat(service.forStudent(STUDENT, COURSE_ID)).get().extracting(LmsSyllabus::getPublishedContent).isEqualTo("First");
        assertThat(syllabus.hasUnpublishedChanges()).isTrue();

        syllabus.publish(NOW.plusSeconds(60));
        assertThat(syllabus.getPublishedContent()).isEqualTo("Second");
        assertThat(syllabus.getFirstPublishedAt()).isEqualTo(NOW);
        assertThat(syllabus.getLastPublishedAt()).isEqualTo(NOW.plusSeconds(60));
        syllabus.publish(NOW.plusSeconds(120));
        assertThat(syllabus.getLastPublishedAt()).as("nothing new to publish").isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void publishingWithoutADraftIsRefused() {
        when(syllabi.findByLmsCourseId(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(FACULTY, COURSE_ID)).isInstanceOfSatisfying(ApiException.class,
                refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void aCourseTheCallerMayNotUseLooksMissingAndNothingIsRead() {
        for (Runnable call : new Runnable[] {
            () -> service.forStudent(99L, COURSE_ID),
            () -> service.forFaculty(99L, COURSE_ID),
            () -> service.saveDraft(99L, COURSE_ID, "Text"),
            () -> service.publish(99L, COURSE_ID)}) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                    refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        verifyNoInteractions(syllabi);
        verify(lmsCourses, never()).findByIdForUpdate(any());
    }

    @Test
    void aDraftNeedsTextWithinTheLimit() {
        assertThatThrownBy(() -> new LmsSyllabus(course, " ", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LmsSyllabus(course, "x".repeat(30_001), NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
