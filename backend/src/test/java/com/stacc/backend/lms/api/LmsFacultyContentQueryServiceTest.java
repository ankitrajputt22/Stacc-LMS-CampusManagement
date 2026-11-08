package com.stacc.backend.lms.api;

import static com.stacc.backend.lms.content.ContentFixtures.material;
import static com.stacc.backend.lms.content.ContentFixtures.module;
import static com.stacc.backend.lms.content.ContentFixtures.topic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsModule;
import com.stacc.backend.lms.content.LmsModuleRepository;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTopicRepository;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.content.LmsTextFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LmsFacultyContentQueryServiceTest {

    private static final Long ACCOUNT_ID = 21L;
    private static final Long COURSE_ID = 7L;

    private final LmsFacultyAccessService accessService = mock(LmsFacultyAccessService.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsModuleRepository modules = mock(LmsModuleRepository.class);
    private final LmsTopicRepository topics = mock(LmsTopicRepository.class);
    private final LmsStudyMaterialRepository materials = mock(LmsStudyMaterialRepository.class);
    private final LmsFacultyContentQueryService service =
            new LmsFacultyContentQueryService(accessService, lmsCourses, modules, topics, materials);

    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE_ID);

    @BeforeEach
    void facultyMemberWhoTeachesTheCourse() {
        when(accessService.canAccessCourse(ACCOUNT_ID, COURSE_ID)).thenReturn(true);
        when(lmsCourses.findWithAcademicContextById(COURSE_ID)).thenReturn(Optional.of(course));
    }

    @Test
    void draftAndPublishedContentIsShownWithTheCourseButArchivedContentIsNot() {
        LmsModule draft = module(course, 11, 1, LmsContentStatus.DRAFT);
        LmsModule published = module(course, 12, 2, LmsContentStatus.PUBLISHED);
        LmsModule archived = module(course, 13, 3, LmsContentStatus.ARCHIVED);
        when(modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(COURSE_ID, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(draft, published, archived));
        when(topics.findAllInCourse(COURSE_ID, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE)).thenReturn(List.of(
                topic(draft, 31, 1, LmsContentStatus.PUBLISHED),
                topic(draft, 32, 2, LmsContentStatus.DRAFT),
                topic(draft, 33, 3, LmsContentStatus.ARCHIVED),
                topic(archived, 34, 1, LmsContentStatus.DRAFT)));

        LmsFacultyCourseContentResponse content = service.getCourseContent(ACCOUNT_ID, COURSE_ID);

        assertEquals(new LmsFacultyCourseContentResponse(7L, "TST301", "Test Data Structures", new BigDecimal("4.00"), 3,
                "BTECH", "2026-27", "DRAFT", List.of(
                        new LmsFacultyModuleContentResponse(11L, "Module 11", null, 1, LmsContentStatus.DRAFT, List.of(
                                new LmsFacultyTopicContentResponse(31L, "Topic 31", "Text of topic 31", 1, LmsContentStatus.PUBLISHED, List.of(), LmsTextFormat.PLAIN),
                                new LmsFacultyTopicContentResponse(32L, "Topic 32", "Text of topic 32", 2, LmsContentStatus.DRAFT, List.of(), LmsTextFormat.PLAIN))),
                        new LmsFacultyModuleContentResponse(12L, "Module 12", null, 2, LmsContentStatus.PUBLISHED, List.of()))),
                content);
    }

    @Test
    void aDraftPageOpensForPreviewWithThePagesAroundItAndTheCourseFromTheStoredPage() {
        LmsModule draft = module(course, 11, 1, LmsContentStatus.DRAFT);
        LmsModule archived = module(course, 13, 3, LmsContentStatus.ARCHIVED);
        LmsTopic first = topic(draft, 31, 1, LmsContentStatus.PUBLISHED);
        LmsTopic second = topic(draft, 32, 2, LmsContentStatus.DRAFT);
        LmsTopic gone = topic(draft, 33, 3, LmsContentStatus.ARCHIVED);
        LmsTopic inArchived = topic(archived, 34, 1, LmsContentStatus.DRAFT);
        when(modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(COURSE_ID, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(draft, archived));
        when(topics.findAllInCourse(COURSE_ID, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(first, second, gone, inArchived));
        for (LmsTopic topic : List.of(first, second, gone, inArchived)) {
            when(topics.findWithModuleById(topic.getId())).thenReturn(Optional.of(topic));
        }

        LmsFacultyPageResponse page = service.getPage(ACCOUNT_ID, 32L);

        assertEquals(LmsContentStatus.DRAFT, page.status());
        assertEquals(LmsContentStatus.DRAFT, page.moduleStatus());
        assertEquals(7L, page.lmsCourseId());
        assertEquals(new LmsPageLink(31L, "Topic 31", "Module 11"), page.previous());
        assertEquals(null, page.next(), "the archived page is not listed");
        for (Long hidden : List.of(33L, 34L, 999L)) {
            assertEquals(HttpStatus.NOT_FOUND,
                    assertThrows(ApiException.class, () -> service.getPage(ACCOUNT_ID, hidden)).getStatus());
        }
    }

    @Test
    void aPageOfACourseTheFacultyMemberDoesNotTeachLooksMissing() {
        LmsModule draft = module(course, 11, 1, LmsContentStatus.DRAFT);
        when(topics.findWithModuleById(31L)).thenReturn(Optional.of(topic(draft, 31, 1, LmsContentStatus.DRAFT)));
        when(accessService.canAccessCourse(ACCOUNT_ID, COURSE_ID)).thenReturn(false);

        ApiException refused = assertThrows(ApiException.class, () -> service.getPage(ACCOUNT_ID, 31L));

        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        assertEquals("Page not found or unavailable.", refused.getMessage());
    }

    @Test
    void courseWithoutContentGivesAnEmptyModuleList() {
        when(modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(COURSE_ID, LmsContentPolicy.IN_USE))
                .thenReturn(List.of());
        when(topics.findAllInCourse(COURSE_ID, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE)).thenReturn(List.of());

        assertEquals(List.of(), service.getCourseContent(ACCOUNT_ID, COURSE_ID).modules());
    }

    @Test
    void courseTheFacultyMemberMayNotUseIsUnavailableAndNothingIsRead() {
        when(accessService.canAccessCourse(ACCOUNT_ID, COURSE_ID)).thenReturn(false);

        ApiException refused = assertThrows(ApiException.class, () -> service.getCourseContent(ACCOUNT_ID, COURSE_ID));

        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        assertEquals("Course not found or unavailable.", refused.getMessage());
        verifyNoInteractions(lmsCourses, modules, topics, materials);
    }

    @Test
    void theFilesListHasDraftsWithThePageAndModuleStatusButNoArchivedFiles() {
        LmsModule module = module(course, 11, 1, LmsContentStatus.DRAFT);
        LmsTopic topic = topic(module, 31, 1, LmsContentStatus.PUBLISHED);
        when(modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(COURSE_ID, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(module));
        when(topics.findAllInCourse(COURSE_ID, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE)).thenReturn(List.of(topic));
        when(materials.findAllInCourse(COURSE_ID, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(material(topic, 7, 1, LmsContentStatus.DRAFT), material(topic, 9, 2, LmsContentStatus.ARCHIVED)));

        LmsFacultyCourseFilesResponse files = service.getCourseFiles(ACCOUNT_ID, COURSE_ID);

        assertEquals("DRAFT", files.lmsCourseStatus());
        assertEquals(1, files.files().size());
        LmsFacultyCourseFileResponse file = files.files().get(0);
        assertEquals(LmsContentStatus.DRAFT, file.status());
        assertEquals(LmsContentStatus.PUBLISHED, file.pageStatus());
        assertEquals(LmsContentStatus.DRAFT, file.moduleStatus());
        assertEquals(31L, file.pageId());
    }

    @Test
    void draftAndPublishedMaterialsAreShownUnderTheirTopicButArchivedOnesAreNot() {
        LmsModule module = module(course, 11, 1, LmsContentStatus.DRAFT);
        LmsTopic topic = topic(module, 31, 1, LmsContentStatus.DRAFT);
        LmsTopic archivedTopic = topic(module, 32, 2, LmsContentStatus.ARCHIVED);
        when(modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(COURSE_ID, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(module));
        when(topics.findAllInCourse(COURSE_ID, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE)).thenReturn(List.of(topic));
        when(materials.findAllInCourse(COURSE_ID, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(
                        material(topic, 7, 1, LmsContentStatus.DRAFT),
                        material(topic, 8, 2, LmsContentStatus.PUBLISHED),
                        material(topic, 9, 3, LmsContentStatus.ARCHIVED),
                        material(archivedTopic, 10, 1, LmsContentStatus.PUBLISHED)));

        List<LmsFacultyMaterialResponse> shown =
                service.getCourseContent(ACCOUNT_ID, COURSE_ID).modules().get(0).topics().get(0).materials();

        assertEquals(List.of(
                new LmsFacultyMaterialResponse(7L, "Material 7", null, "material-7.pdf", "application/pdf", 1007, 1, LmsContentStatus.DRAFT, null),
                new LmsFacultyMaterialResponse(8L, "Material 8", null, "material-8.pdf", "application/pdf", 1008, 2, LmsContentStatus.PUBLISHED, null)),
                shown);
    }
}
