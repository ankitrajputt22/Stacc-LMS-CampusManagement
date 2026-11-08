package com.stacc.backend.lms.copy;

import static com.stacc.backend.lms.content.ContentFixtures.material;
import static com.stacc.backend.lms.content.ContentFixtures.module;
import static com.stacc.backend.lms.content.ContentFixtures.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsModule;
import com.stacc.backend.lms.content.LmsModuleRepository;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTopicRepository;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.syllabus.LmsSyllabus;
import com.stacc.backend.lms.syllabus.LmsSyllabusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class LmsCourseCopyServiceTest {

    private static final Long FACULTY = 21L;
    private static final Long TARGET = 8L;
    private static final Long SOURCE = 7L;

    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsModuleRepository modules = mock(LmsModuleRepository.class);
    private final LmsTopicRepository topics = mock(LmsTopicRepository.class);
    private final LmsStudyMaterialRepository materials = mock(LmsStudyMaterialRepository.class);
    private final LmsSyllabusRepository syllabi = mock(LmsSyllabusRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final LmsCourseCopyService service = new LmsCourseCopyService(lmsCourses, facultyAccess, modules, topics,
            materials, syllabi, storage, Clock.fixed(Instant.parse("2026-11-08T09:00:00Z"), ZoneOffset.UTC));

    private final LmsCourse source = ContentFixtures.lmsCourse(SOURCE);
    private final LmsCourse target = ContentFixtures.lmsCourse(TARGET);
    private final AtomicLong ids = new AtomicLong(900);
    private final List<LmsStudyMaterial> savedFiles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(facultyAccess.canAccessCourse(FACULTY, TARGET)).thenReturn(true);
        when(lmsCourses.findWithAcademicContextById(TARGET)).thenReturn(Optional.of(target));
        when(lmsCourses.findByIdForUpdate(TARGET)).thenReturn(Optional.of(target));
        when(lmsCourses.findCopySources(any(), any(), eq(FACULTY))).thenReturn(List.of(source));
        when(modules.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(topics.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(materials.save(any())).thenAnswer(call -> {
            savedFiles.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(storage.open(anyString())).thenAnswer(call -> new ByteArrayInputStream(new byte[] {1, 2, 3}));

        LmsModule week1 = module(source, 11, 1, LmsContentStatus.PUBLISHED);
        week1.requirePrevious(true);
        LmsModule archivedModule = module(source, 12, 2, LmsContentStatus.ARCHIVED);
        LmsTopic sorting = topic(week1, 31, 1, LmsContentStatus.PUBLISHED);
        sorting.markRequired(true);
        sorting.changeContentFormat(LmsTextFormat.FORMATTED);
        LmsTopic archivedPage = topic(week1, 32, 2, LmsContentStatus.ARCHIVED);
        when(modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(SOURCE, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(week1, archivedModule));
        when(modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(TARGET, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(module(target, 41, 3, LmsContentStatus.PUBLISHED)));
        when(topics.findAllInCourse(SOURCE, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE)).thenReturn(List.of(sorting, archivedPage));
        when(materials.findAllInCourse(SOURCE, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(material(sorting, 71, 1, LmsContentStatus.PUBLISHED), material(sorting, 72, 2, LmsContentStatus.ARCHIVED)));
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", ids.incrementAndGet());
        return entity;
    }

    @Test
    void contentArrivesAsDraftsAfterTheTargetsOwnWithItsFilesUnderNewKeys() {
        LmsSyllabus sourceSyllabus = new LmsSyllabus(source, "## Overview", Instant.parse("2026-01-01T00:00:00Z"));
        when(syllabi.findByLmsCourseId(SOURCE)).thenReturn(Optional.of(sourceSyllabus));
        when(syllabi.findByLmsCourseIdForUpdate(TARGET)).thenReturn(Optional.empty());

        LmsCourseCopyService.CopyResult result = service.copy(FACULTY, TARGET, SOURCE);

        assertThat(result).isEqualTo(new LmsCourseCopyService.CopyResult(1, 1, 1, true));
        verify(modules).saveAndFlush(org.mockito.ArgumentMatchers.argThat(module -> module.getPosition() == 4
                && module.getStatus() == LmsContentStatus.DRAFT && module.isRequiresPrevious() && module.getLmsCourse() == target));
        verify(topics).saveAndFlush(org.mockito.ArgumentMatchers.argThat(page -> page.isRequired()
                && page.getContentFormat() == LmsTextFormat.FORMATTED && page.getStatus() == LmsContentStatus.DRAFT));
        LmsStudyMaterial copied = savedFiles.getFirst();
        assertThat(copied.getStatus()).isEqualTo(LmsContentStatus.DRAFT);
        assertThat(copied.getObjectKey()).startsWith("lms/courses/8/topics/").isNotEqualTo("lms/test/key-71");
        verify(storage).open("lms/test/key-71");
        verify(storage).store(eq(copied.getObjectKey()), any(), eq(1071L), eq("application/pdf"));
        verify(syllabi).save(org.mockito.ArgumentMatchers.argThat(syllabus -> "## Overview".equals(syllabus.getDraftContent())
                && !syllabus.isPublished()));
    }

    @Test
    void anExistingSyllabusIsNeverReplaced() {
        when(syllabi.findByLmsCourseId(SOURCE)).thenReturn(Optional.of(new LmsSyllabus(source, "Old", Instant.EPOCH)));
        when(syllabi.findByLmsCourseIdForUpdate(TARGET)).thenReturn(Optional.of(new LmsSyllabus(target, "Mine", Instant.EPOCH)));

        assertThat(service.copy(FACULTY, TARGET, SOURCE).syllabus()).isFalse();
        verify(syllabi, never()).save(any());
    }

    @Test
    void aSourceTheFacultyMemberDidNotTeachOrATargetTheyDoNotTeachLooksMissing() {
        assertThatThrownBy(() -> service.copy(FACULTY, TARGET, 99L)).isInstanceOfSatisfying(ApiException.class,
                refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.copy(55L, TARGET, SOURCE)).isInstanceOfSatisfying(ApiException.class,
                refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(modules, never()).saveAndFlush(any());
    }

    @Test
    void aStorageFailureRemovesTheFilesAlreadyCopiedAndAnswers503() {
        when(storage.open(anyString())).thenThrow(new ObjectStorageException("down", null));

        assertThatThrownBy(() -> service.copy(FACULTY, TARGET, SOURCE)).isInstanceOfSatisfying(ApiException.class,
                refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(materials, never()).save(any());
    }
}
