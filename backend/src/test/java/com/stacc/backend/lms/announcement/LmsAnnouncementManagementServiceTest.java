package com.stacc.backend.lms.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The faculty member behind ACCOUNT_ID teaches course 7 and no other. The faculty access rules themselves
 * (inactive profile or assignment, archived course) are tested with LmsFacultyAccessService; here a
 * refusal from it is shown to stop every change.
 */
class LmsAnnouncementManagementServiceTest {

    private static final Long ACCOUNT_ID = 21L;
    private static final Long OTHER_ACCOUNT_ID = 22L;
    private static final Instant NOW = Instant.parse("2026-10-15T08:00:00Z");

    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsAnnouncementRepository announcements = mock(LmsAnnouncementRepository.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsAnnouncementManagementService service = new LmsAnnouncementManagementService(
            lmsCourses, announcements, facultyAccess, Clock.fixed(NOW, ZoneOffset.UTC));

    private final LmsCourse course = ContentFixtures.lmsCourse(7);
    private final LmsAnnouncement draft = announcement(course, 5);
    private final LmsAnnouncement foreign = announcement(ContentFixtures.lmsCourse(8), 6);

    private static LmsAnnouncement announcement(LmsCourse course, long id) {
        LmsAnnouncement created = new LmsAnnouncement(course, "Announcement " + id, "Message " + id);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    @BeforeEach
    void facultyMemberWhoTeachesCourseSeven() {
        when(facultyAccess.canAccessCourse(ACCOUNT_ID, 7L)).thenReturn(true);
        when(lmsCourses.findById(7L)).thenReturn(Optional.of(course));
        when(announcements.findById(5L)).thenReturn(Optional.of(draft));
        when(announcements.findById(6L)).thenReturn(Optional.of(foreign));
        when(announcements.save(any(LmsAnnouncement.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void newAnnouncementIsAnUnpublishedDraftOfTheCourse() {
        LmsAnnouncement created = service.create(ACCOUNT_ID, 7L, "Exam schedule", "Monday at 9.");

        assertSame(course, created.getLmsCourse());
        assertEquals(LmsContentStatus.DRAFT, created.getStatus());
        assertEquals(null, created.getPublishedAt());
        verify(announcements).save(created);
    }

    @Test
    void courseTheFacultyMemberDoesNotTeachIsUnavailable() {
        assertRefused(HttpStatus.NOT_FOUND, LmsAnnouncementManagementService.COURSE_UNAVAILABLE,
                () -> service.create(ACCOUNT_ID, 8L, "Title", "Message"));
        assertRefused(HttpStatus.NOT_FOUND, LmsAnnouncementManagementService.COURSE_UNAVAILABLE,
                () -> service.create(OTHER_ACCOUNT_ID, 7L, "Title", "Message"));

        verify(announcements, never()).save(any());
    }

    @Test
    void guessedIdsOfOtherCoursesAndMissingAnnouncementsLookTheSame() {
        for (Executable action : List.<Executable>of(
                () -> service.update(ACCOUNT_ID, 6L, "Taken", "Taken"),
                () -> service.publish(ACCOUNT_ID, 6L),
                () -> service.archive(ACCOUNT_ID, 6L),
                () -> service.update(OTHER_ACCOUNT_ID, 5L, "Taken", "Taken"),
                () -> service.publish(OTHER_ACCOUNT_ID, 5L),
                () -> service.archive(OTHER_ACCOUNT_ID, 5L),
                () -> service.publish(ACCOUNT_ID, 999L),
                () -> service.publish(ACCOUNT_ID, null))) {
            assertRefused(HttpStatus.NOT_FOUND, LmsAnnouncementManagementService.ANNOUNCEMENT_UNAVAILABLE, action);
        }

        assertEquals("Announcement 6", foreign.getTitle());
        assertEquals(LmsContentStatus.DRAFT, foreign.getStatus());
        assertEquals(LmsContentStatus.DRAFT, draft.getStatus());
    }

    @Test
    void publishingUsesTheCurrentTimeOnceAndCanBeRepeated() {
        service.publish(ACCOUNT_ID, 5L);
        LmsAnnouncement again = service.publish(ACCOUNT_ID, 5L);

        assertEquals(LmsContentStatus.PUBLISHED, again.getStatus());
        assertEquals(NOW, again.getPublishedAt());
    }

    @Test
    void publishedAnnouncementCanBeCorrected() {
        service.publish(ACCOUNT_ID, 5L);

        LmsAnnouncement corrected = service.update(ACCOUNT_ID, 5L, "Exam moved", "Tuesday at 9.");

        assertEquals("Exam moved", corrected.getTitle());
        assertEquals(LmsContentStatus.PUBLISHED, corrected.getStatus());
        assertEquals(NOW, corrected.getPublishedAt());
    }

    @Test
    void archivingCanBeRepeatedAndEndsEditingAndPublishing() {
        service.archive(ACCOUNT_ID, 5L);
        service.archive(ACCOUNT_ID, 5L);
        assertEquals(LmsContentStatus.ARCHIVED, draft.getStatus());

        assertRefused(HttpStatus.CONFLICT, LmsAnnouncementManagementService.ANNOUNCEMENT_ARCHIVED,
                () -> service.update(ACCOUNT_ID, 5L, "Back", "Back"));
        assertRefused(HttpStatus.CONFLICT, LmsAnnouncementManagementService.ANNOUNCEMENT_ARCHIVED,
                () -> service.publish(ACCOUNT_ID, 5L));
        verify(announcements, never()).delete(any());
        verify(announcements, never()).deleteById(any());
    }
}
