package com.stacc.backend.lms.api;

import static com.stacc.backend.lms.content.ContentFixtures.material;
import static com.stacc.backend.lms.content.ContentFixtures.module;
import static com.stacc.backend.lms.content.ContentFixtures.topic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsModule;
import com.stacc.backend.lms.content.LmsModuleRepository;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTopicRepository;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * The repositories are stand-ins here, so they can hand back content in any state. That shows the
 * student rule holds on its own, even if a query ever returned too much.
 */
class LmsStudentContentQueryServiceTest {

    private static final Long ACCOUNT_ID = 12L;
    private static final Long COURSE_ID = 7L;

    private final LmsStudentAccessService accessService = mock(LmsStudentAccessService.class);
    private final LmsModuleRepository modules = mock(LmsModuleRepository.class);
    private final LmsTopicRepository topics = mock(LmsTopicRepository.class);
    private final LmsStudyMaterialRepository materials = mock(LmsStudyMaterialRepository.class);
    private final LmsStudentContentQueryService service =
            new LmsStudentContentQueryService(accessService, modules, topics, materials);

    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE_ID);

    @BeforeEach
    void studentWhoMayUseTheCourse() {
        when(accessService.canAccessCourse(ACCOUNT_ID, COURSE_ID)).thenReturn(true);
    }

    private void stored(List<LmsModule> storedModules, List<LmsTopic> storedTopics) {
        when(modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(COURSE_ID, LmsContentPolicy.PUBLISHED_ONLY))
                .thenReturn(storedModules);
        when(topics.findAllInCourse(COURSE_ID, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY))
                .thenReturn(storedTopics);
    }

    @Test
    void onlyPublishedTopicsOfPublishedModulesAreShown() {
        LmsModule published = module(course, 11, 1, LmsContentStatus.PUBLISHED);
        LmsModule draft = module(course, 12, 2, LmsContentStatus.DRAFT);
        LmsModule archived = module(course, 13, 3, LmsContentStatus.ARCHIVED);
        stored(List.of(published, draft, archived), List.of(
                topic(published, 31, 1, LmsContentStatus.PUBLISHED),
                topic(published, 32, 2, LmsContentStatus.DRAFT),
                topic(published, 33, 3, LmsContentStatus.ARCHIVED),
                topic(draft, 34, 1, LmsContentStatus.PUBLISHED),
                topic(archived, 35, 1, LmsContentStatus.PUBLISHED)));

        LmsCourseContentResponse content = service.getPublishedContent(ACCOUNT_ID, COURSE_ID);

        assertEquals(new LmsCourseContentResponse(List.of(new LmsCourseModuleResponse(11L, "Module 11", null,
                List.of(new LmsCourseTopicResponse(31L, "Topic 31", "Text of topic 31", List.of()))))), content);
    }

    @Test
    void publishedModuleWithoutPublishedTopicsIsShownEmpty() {
        LmsModule published = module(course, 11, 1, LmsContentStatus.PUBLISHED);
        stored(List.of(published), List.of());

        assertEquals(List.of(), service.getPublishedContent(ACCOUNT_ID, COURSE_ID).modules().get(0).topics());
    }

    @Test
    void courseWithNothingPublishedGivesAnEmptyModuleList() {
        stored(List.of(), List.of());

        assertEquals(new LmsCourseContentResponse(List.of()), service.getPublishedContent(ACCOUNT_ID, COURSE_ID));
    }

    @Test
    void contentKeepsTheCourseOrderGivenByTheQueries() {
        LmsModule second = module(course, 9, 2, LmsContentStatus.PUBLISHED);
        LmsModule first = module(course, 15, 1, LmsContentStatus.PUBLISHED);
        stored(List.of(first, second), List.of(
                topic(second, 40, 1, LmsContentStatus.PUBLISHED),
                topic(first, 41, 1, LmsContentStatus.PUBLISHED),
                topic(first, 39, 2, LmsContentStatus.PUBLISHED)));

        LmsCourseContentResponse content = service.getPublishedContent(ACCOUNT_ID, COURSE_ID);

        assertEquals(List.of(15L, 9L), content.modules().stream().map(LmsCourseModuleResponse::id).toList());
        assertEquals(List.of(41L, 39L), content.modules().get(0).topics().stream().map(LmsCourseTopicResponse::id).toList());
        assertEquals(List.of(40L), content.modules().get(1).topics().stream().map(LmsCourseTopicResponse::id).toList());
    }

    @Test
    void courseTheStudentMayNotUseIsUnavailableAndNothingIsRead() {
        when(accessService.canAccessCourse(ACCOUNT_ID, COURSE_ID)).thenReturn(false);

        ApiException refused = assertThrows(ApiException.class, () -> service.getPublishedContent(ACCOUNT_ID, COURSE_ID));

        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        assertEquals("Course not found or unavailable.", refused.getMessage());
        verifyNoInteractions(modules, topics, materials);
    }

    @Test
    void readingContentOnlyAsksForPublishedContent() {
        stored(List.of(), List.of());

        service.getPublishedContent(ACCOUNT_ID, COURSE_ID);

        verify(modules).findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(COURSE_ID, LmsContentPolicy.PUBLISHED_ONLY);
        verify(topics).findAllInCourse(COURSE_ID, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY);
        verify(materials).findAllInCourse(COURSE_ID, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY,
                LmsContentPolicy.PUBLISHED_ONLY);
        verifyNoMoreInteractions(modules, topics, materials);
    }

    // Study materials

    private List<Long> materialIdsShown(List<LmsStudyMaterial> storedMaterials, LmsModule module, LmsTopic... storedTopics) {
        stored(List.of(module), List.of(storedTopics));
        when(materials.findAllInCourse(COURSE_ID, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY,
                LmsContentPolicy.PUBLISHED_ONLY)).thenReturn(storedMaterials);
        return service.getPublishedContent(ACCOUNT_ID, COURSE_ID).modules().stream()
                .flatMap(shownModule -> shownModule.topics().stream())
                .flatMap(shownTopic -> shownTopic.materials().stream())
                .map(LmsCourseMaterialResponse::id)
                .toList();
    }

    @Test
    void publishedMaterialOfAPublishedTopicInAPublishedModuleIsShownWithItsFileDetails() {
        LmsModule module = module(course, 11, 1, LmsContentStatus.PUBLISHED);
        LmsTopic topic = topic(module, 31, 1, LmsContentStatus.PUBLISHED);
        stored(List.of(module), List.of(topic));
        when(materials.findAllInCourse(COURSE_ID, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY,
                LmsContentPolicy.PUBLISHED_ONLY)).thenReturn(List.of(material(topic, 7, 1, LmsContentStatus.PUBLISHED)));

        LmsCourseTopicResponse shown = service.getPublishedContent(ACCOUNT_ID, COURSE_ID).modules().get(0).topics().get(0);

        assertEquals(List.of(new LmsCourseMaterialResponse(7L, "Material 7", null, "material-7.pdf", "application/pdf", 1007)),
                shown.materials());
    }

    @Test
    void materialIsHiddenUnlessItsModuleTopicAndItselfArePublished() {
        LmsModule published = module(course, 11, 1, LmsContentStatus.PUBLISHED);
        LmsTopic publishedTopic = topic(published, 31, 1, LmsContentStatus.PUBLISHED);
        LmsModule draftModule = module(course, 12, 2, LmsContentStatus.DRAFT);
        LmsTopic topicInDraftModule = topic(draftModule, 32, 1, LmsContentStatus.PUBLISHED);
        LmsTopic draftTopic = topic(published, 33, 2, LmsContentStatus.DRAFT);

        assertEquals(List.of(), materialIdsShown(
                List.of(material(topicInDraftModule, 1, 1, LmsContentStatus.PUBLISHED)), published, publishedTopic));
        assertEquals(List.of(), materialIdsShown(
                List.of(material(draftTopic, 2, 1, LmsContentStatus.PUBLISHED)), published, publishedTopic));
        assertEquals(List.of(), materialIdsShown(
                List.of(material(publishedTopic, 3, 1, LmsContentStatus.DRAFT)), published, publishedTopic));
        assertEquals(List.of(), materialIdsShown(
                List.of(material(publishedTopic, 4, 1, LmsContentStatus.ARCHIVED)), published, publishedTopic));
        assertEquals(List.of(5L), materialIdsShown(
                List.of(material(publishedTopic, 5, 1, LmsContentStatus.PUBLISHED)), published, publishedTopic));
    }

    @Test
    void materialsKeepTheTopicOrderGivenByTheQuery() {
        LmsModule module = module(course, 11, 1, LmsContentStatus.PUBLISHED);
        LmsTopic topic = topic(module, 31, 1, LmsContentStatus.PUBLISHED);

        assertEquals(List.of(9L, 3L, 7L), materialIdsShown(List.of(
                material(topic, 9, 1, LmsContentStatus.PUBLISHED),
                material(topic, 3, 2, LmsContentStatus.PUBLISHED),
                material(topic, 7, 3, LmsContentStatus.PUBLISHED)), module, topic));
    }
}
