package com.stacc.backend.lms.content;

import static com.stacc.backend.lms.content.ContentFixtures.module;
import static com.stacc.backend.lms.content.ContentFixtures.topic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

/**
 * The faculty member behind ACCOUNT_ID teaches the course with ID 7 and no other. Every change is
 * checked against the course found from stored data, never against anything the request says.
 */
class LmsContentManagementServiceTest {

    // Made-up IDs used only by these tests.
    private static final Long ACCOUNT_ID = 21L;
    private static final Long OTHER_ACCOUNT_ID = 22L;
    private static final Long COURSE_ID = 7L;
    private static final Long OTHER_COURSE_ID = 8L;

    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsModuleRepository modules = mock(LmsModuleRepository.class);
    private final LmsTopicRepository topics = mock(LmsTopicRepository.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsContentManagementService service =
            new LmsContentManagementService(lmsCourses, modules, topics, facultyAccess);

    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE_ID);
    private final LmsCourse otherCourse = ContentFixtures.lmsCourse(OTHER_COURSE_ID);
    private final LmsModule first = module(course, 11, 1, LmsContentStatus.PUBLISHED);
    private final LmsModule second = module(course, 12, 2, LmsContentStatus.DRAFT);
    private final LmsModule third = module(course, 13, 3, LmsContentStatus.DRAFT);
    private final LmsModule archived = module(course, 14, 4, LmsContentStatus.ARCHIVED);
    private final LmsModule foreign = module(otherCourse, 21, 1, LmsContentStatus.DRAFT);
    private final LmsTopic topicA = topic(first, 31, 1, LmsContentStatus.PUBLISHED);
    private final LmsTopic topicB = topic(first, 32, 2, LmsContentStatus.DRAFT);
    private final LmsTopic foreignTopic = topic(foreign, 41, 1, LmsContentStatus.DRAFT);

    @BeforeEach
    void facultyMemberWhoTeachesCourseSeven() {
        when(facultyAccess.canAccessCourse(ACCOUNT_ID, COURSE_ID)).thenReturn(true);
        when(lmsCourses.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(modules.save(any(LmsModule.class))).thenAnswer(call -> call.getArgument(0));
        when(topics.save(any(LmsTopic.class))).thenAnswer(call -> call.getArgument(0));
        for (LmsModule module : List.of(first, second, third, archived, foreign)) {
            when(modules.findById(module.getId())).thenReturn(Optional.of(module));
        }
        for (LmsTopic topic : List.of(topicA, topicB, foreignTopic)) {
            when(topics.findWithModuleById(topic.getId())).thenReturn(Optional.of(topic));
        }
        currentModules(first, second, third);
        currentTopics(first, topicA, topicB);
    }

    private void currentModules(LmsModule... current) {
        when(modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(COURSE_ID, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(current));
    }

    private void currentTopics(LmsModule module, LmsTopic... current) {
        when(topics.findAllByLmsModuleIdAndStatusInOrderByPositionAscIdAsc(module.getId(), LmsContentPolicy.IN_USE))
                .thenReturn(List.of(current));
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    private static List<Integer> positionsOf(LmsModule... list) {
        return Arrays.stream(list).map(LmsModule::getPosition).toList();
    }

    // Modules

    @Test
    void newModuleIsADraftAddedAfterTheCurrentModules() {
        LmsModule created = service.createModule(ACCOUNT_ID, COURSE_ID, " Trees ", " ");

        assertSame(course, created.getLmsCourse());
        assertEquals("Trees", created.getTitle());
        assertNull(created.getDescription());
        assertEquals(4, created.getPosition());
        assertEquals(LmsContentStatus.DRAFT, created.getStatus());
        verify(modules).save(created);
    }

    @Test
    void firstModuleOfACourseIsNumberOne() {
        currentModules();

        assertEquals(1, service.createModule(ACCOUNT_ID, COURSE_ID, "Introduction", null).getPosition());
    }

    @Test
    void courseTheFacultyMemberDoesNotTeachIsUnavailable() {
        assertRefused(HttpStatus.NOT_FOUND, LmsContentManagementService.COURSE_UNAVAILABLE,
                () -> service.createModule(ACCOUNT_ID, OTHER_COURSE_ID, "Trees", null));
        assertRefused(HttpStatus.NOT_FOUND, LmsContentManagementService.COURSE_UNAVAILABLE,
                () -> service.createModule(OTHER_ACCOUNT_ID, COURSE_ID, "Trees", null));
        assertRefused(HttpStatus.NOT_FOUND, LmsContentManagementService.COURSE_UNAVAILABLE,
                () -> service.reorderModules(OTHER_ACCOUNT_ID, COURSE_ID, List.of(11L, 12L, 13L)));

        verify(modules, never()).save(any());
        assertEquals(List.of(1, 2, 3), positionsOf(first, second, third));
    }

    @Test
    void moduleOfAnotherCourseOrAMissingModuleLooksTheSame() {
        for (Executable action : List.<Executable>of(
                () -> service.updateModule(ACCOUNT_ID, foreign.getId(), "Taken", null),
                () -> service.publishModule(ACCOUNT_ID, foreign.getId()),
                () -> service.archiveModule(ACCOUNT_ID, foreign.getId()),
                () -> service.createTopic(ACCOUNT_ID, foreign.getId(), "Taken", null),
                () -> service.reorderTopics(ACCOUNT_ID, foreign.getId(), List.of()),
                () -> service.publishModule(ACCOUNT_ID, 999L),
                () -> service.publishModule(ACCOUNT_ID, null),
                () -> service.publishModule(OTHER_ACCOUNT_ID, first.getId()))) {
            assertRefused(HttpStatus.NOT_FOUND, LmsContentManagementService.MODULE_UNAVAILABLE, action);
        }

        assertEquals("Module 21", foreign.getTitle());
        assertEquals(LmsContentStatus.DRAFT, foreign.getStatus());
        verify(topics, never()).save(any());
    }

    @Test
    void editingAModuleChangesItsTextOnly() {
        LmsModule edited = service.updateModule(ACCOUNT_ID, first.getId(), "Getting started", "Read this first.");

        assertSame(first, edited);
        assertEquals("Getting started", edited.getTitle());
        assertEquals("Read this first.", edited.getDescription());
        assertEquals(1, edited.getPosition());
        assertEquals(LmsContentStatus.PUBLISHED, edited.getStatus());
    }

    @Test
    void publishingAModuleCanBeRepeatedAndLeavesItsTopicsAlone() {
        service.publishModule(ACCOUNT_ID, second.getId());
        service.publishModule(ACCOUNT_ID, second.getId());

        assertEquals(LmsContentStatus.PUBLISHED, second.getStatus());
        verifyNoInteractions(topics);
    }

    @Test
    void archivingAModuleCanBeRepeatedAndKeepsItsTopics() {
        service.archiveModule(ACCOUNT_ID, first.getId());
        service.archiveModule(ACCOUNT_ID, first.getId());

        assertEquals(LmsContentStatus.ARCHIVED, first.getStatus());
        assertEquals(LmsContentStatus.PUBLISHED, topicA.getStatus());
        assertEquals(LmsContentStatus.DRAFT, topicB.getStatus());
        verifyNoInteractions(topics);
        verify(modules, never()).delete(any());
        verify(modules, never()).deleteById(any());
    }

    @Test
    void archivedModuleCannotBeEditedPublishedReorderedOrGivenTopics() {
        for (Executable action : List.<Executable>of(
                () -> service.updateModule(ACCOUNT_ID, archived.getId(), "Back", null),
                () -> service.publishModule(ACCOUNT_ID, archived.getId()),
                () -> service.createTopic(ACCOUNT_ID, archived.getId(), "New", null),
                () -> service.reorderTopics(ACCOUNT_ID, archived.getId(), List.of()))) {
            assertRefused(HttpStatus.CONFLICT, LmsContentManagementService.MODULE_ARCHIVED, action);
        }

        assertEquals(LmsContentStatus.ARCHIVED, archived.getStatus());
        assertEquals("Module 14", archived.getTitle());
    }

    @Test
    void modulesAreNumberedInTheRequestedOrder() {
        List<LmsModule> ordered = service.reorderModules(ACCOUNT_ID, COURSE_ID, List.of(13L, 11L, 12L));

        assertEquals(List.of(third, first, second), ordered);
        assertEquals(List.of(1, 2, 3), positionsOf(third, first, second));
        assertEquals(4, archived.getPosition());
    }

    @Test
    void moduleOrderMustListEveryCurrentModuleOfTheCourseExactlyOnce() {
        for (List<Long> wrong : Arrays.<List<Long>>asList(
                List.of(11L, 11L, 12L, 13L),
                List.of(11L, 12L),
                List.of(11L, 12L, 13L, 21L),
                List.of(11L, 12L, 21L),
                List.of(11L, 12L, 13L, 14L),
                List.of(),
                Arrays.asList(11L, 12L, null),
                null)) {
            assertRefused(HttpStatus.BAD_REQUEST, LmsContentManagementService.MODULE_ORDER_MISMATCH,
                    () -> service.reorderModules(ACCOUNT_ID, COURSE_ID, wrong));
        }

        assertEquals(List.of(1, 2, 3), positionsOf(first, second, third));
    }

    // Topics

    @Test
    void newTopicIsADraftAddedAfterTheModulesCurrentTopics() {
        LmsTopic created = service.createTopic(ACCOUNT_ID, first.getId(), "Recursion", "Line one\nLine two");

        assertSame(first, created.getLmsModule());
        assertEquals("Line one\nLine two", created.getContent());
        assertEquals(3, created.getPosition());
        assertEquals(LmsContentStatus.DRAFT, created.getStatus());
        verify(topics).save(created);
    }

    @Test
    void topicOfAnotherCourseOrAMissingTopicLooksTheSame() {
        for (Executable action : List.<Executable>of(
                () -> service.updateTopic(ACCOUNT_ID, foreignTopic.getId(), "Taken", null),
                () -> service.publishTopic(ACCOUNT_ID, foreignTopic.getId()),
                () -> service.archiveTopic(ACCOUNT_ID, foreignTopic.getId()),
                () -> service.archiveTopic(ACCOUNT_ID, 999L),
                () -> service.archiveTopic(ACCOUNT_ID, null),
                () -> service.updateTopic(OTHER_ACCOUNT_ID, topicA.getId(), "Taken", null))) {
            assertRefused(HttpStatus.NOT_FOUND, LmsContentManagementService.TOPIC_UNAVAILABLE, action);
        }

        assertEquals("Topic 41", foreignTopic.getTitle());
        assertEquals(LmsContentStatus.DRAFT, foreignTopic.getStatus());
        assertEquals("Topic 31", topicA.getTitle());
    }

    @Test
    void editingATopicChangesItsTextOnly() {
        LmsTopic edited = service.updateTopic(ACCOUNT_ID, topicB.getId(), "Base cases", null);

        assertEquals("Base cases", edited.getTitle());
        assertNull(edited.getContent());
        assertEquals(2, edited.getPosition());
        assertEquals(LmsContentStatus.DRAFT, edited.getStatus());
    }

    @Test
    void publishingATopicCanBeRepeatedAndDoesNotPublishItsModule() {
        LmsTopic inDraftModule = topic(second, 33, 1, LmsContentStatus.DRAFT);
        when(topics.findWithModuleById(33L)).thenReturn(Optional.of(inDraftModule));

        service.publishTopic(ACCOUNT_ID, 33L);
        service.publishTopic(ACCOUNT_ID, 33L);

        assertEquals(LmsContentStatus.PUBLISHED, inDraftModule.getStatus());
        assertEquals(LmsContentStatus.DRAFT, second.getStatus());
    }

    @Test
    void archivedTopicCannotBeEditedOrPublishedButArchivingAgainIsHarmless() {
        service.archiveTopic(ACCOUNT_ID, topicA.getId());
        service.archiveTopic(ACCOUNT_ID, topicA.getId());
        assertEquals(LmsContentStatus.ARCHIVED, topicA.getStatus());

        assertRefused(HttpStatus.CONFLICT, LmsContentManagementService.TOPIC_ARCHIVED,
                () -> service.updateTopic(ACCOUNT_ID, topicA.getId(), "Back", null));
        assertRefused(HttpStatus.CONFLICT, LmsContentManagementService.TOPIC_ARCHIVED,
                () -> service.publishTopic(ACCOUNT_ID, topicA.getId()));
        verify(topics, never()).delete(any());
    }

    @Test
    void topicOfAnArchivedModuleCannotBeEditedOrPublishedButCanBeArchived() {
        LmsTopic inArchivedModule = topic(archived, 35, 1, LmsContentStatus.DRAFT);
        when(topics.findWithModuleById(35L)).thenReturn(Optional.of(inArchivedModule));

        assertRefused(HttpStatus.CONFLICT, LmsContentManagementService.MODULE_ARCHIVED,
                () -> service.updateTopic(ACCOUNT_ID, 35L, "Back", null));
        assertRefused(HttpStatus.CONFLICT, LmsContentManagementService.MODULE_ARCHIVED,
                () -> service.publishTopic(ACCOUNT_ID, 35L));

        service.archiveTopic(ACCOUNT_ID, 35L);
        assertEquals(LmsContentStatus.ARCHIVED, inArchivedModule.getStatus());
    }

    @Test
    void topicsAreNumberedInTheRequestedOrder() {
        List<LmsTopic> ordered = service.reorderTopics(ACCOUNT_ID, first.getId(), List.of(32L, 31L));

        assertEquals(List.of(topicB, topicA), ordered);
        assertEquals(1, topicB.getPosition());
        assertEquals(2, topicA.getPosition());
    }

    @Test
    void topicOrderMustListEveryCurrentTopicOfTheModuleExactlyOnce() {
        for (List<Long> wrong : Arrays.<List<Long>>asList(
                List.of(31L, 31L),
                List.of(31L),
                List.of(31L, 32L, 41L),
                List.of(31L, 41L),
                null)) {
            assertRefused(HttpStatus.BAD_REQUEST, LmsContentManagementService.TOPIC_ORDER_MISMATCH,
                    () -> service.reorderTopics(ACCOUNT_ID, first.getId(), wrong));
        }

        assertEquals(1, topicA.getPosition());
        assertEquals(2, topicB.getPosition());
    }
}
