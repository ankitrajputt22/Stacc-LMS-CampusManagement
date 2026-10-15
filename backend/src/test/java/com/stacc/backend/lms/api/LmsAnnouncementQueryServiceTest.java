package com.stacc.backend.lms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.announcement.LmsAnnouncement;
import com.stacc.backend.lms.announcement.LmsAnnouncementRepository;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The repository is a stand-in, so it can hand back announcements in any state. That shows each side's
 * rule holds on its own, even if a query ever returned too much.
 */
class LmsAnnouncementQueryServiceTest {

    private static final Long STUDENT_ID = 12L;
    private static final Long FACULTY_ID = 21L;
    private static final Long COURSE_ID = 7L;

    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsAnnouncementRepository announcements = mock(LmsAnnouncementRepository.class);
    private final LmsAnnouncementQueryService service =
            new LmsAnnouncementQueryService(studentAccess, facultyAccess, announcements);

    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE_ID);

    private LmsAnnouncement announcement(long id, LmsContentStatus status, String publishedAt) {
        LmsAnnouncement created = new LmsAnnouncement(course, "Announcement " + id, "Message " + id);
        ReflectionTestUtils.setField(created, "id", id);
        ReflectionTestUtils.setField(created, "status", status);
        ReflectionTestUtils.setField(created, "publishedAt", publishedAt == null ? null : Instant.parse(publishedAt));
        return created;
    }

    @BeforeEach
    void accessForTheirOwnSide() {
        when(studentAccess.canAccessCourse(STUDENT_ID, COURSE_ID)).thenReturn(true);
        when(facultyAccess.canAccessCourse(FACULTY_ID, COURSE_ID)).thenReturn(true);
    }

    @Test
    void studentsGetOnlyPublishedAnnouncementsInTheGivenNewestFirstOrder() {
        when(announcements.findAllByLmsCourseIdAndStatusOrderByPublishedAtDescIdDesc(COURSE_ID, LmsContentStatus.PUBLISHED))
                .thenReturn(List.of(
                        announcement(3, LmsContentStatus.PUBLISHED, "2026-10-15T09:00:00Z"),
                        announcement(2, LmsContentStatus.DRAFT, null),
                        announcement(1, LmsContentStatus.PUBLISHED, "2026-10-14T09:00:00Z"),
                        announcement(4, LmsContentStatus.ARCHIVED, "2026-10-16T09:00:00Z")));

        assertEquals(List.of(
                new LmsCourseAnnouncementResponse(3L, "Announcement 3", "Message 3", Instant.parse("2026-10-15T09:00:00Z")),
                new LmsCourseAnnouncementResponse(1L, "Announcement 1", "Message 1", Instant.parse("2026-10-14T09:00:00Z"))),
                service.getPublishedAnnouncements(STUDENT_ID, COURSE_ID));
        verify(announcements).findAllByLmsCourseIdAndStatusOrderByPublishedAtDescIdDesc(COURSE_ID, LmsContentStatus.PUBLISHED);
    }

    @Test
    void courseWithoutAnnouncementsGivesAnEmptyList() {
        assertEquals(List.of(), service.getPublishedAnnouncements(STUDENT_ID, COURSE_ID));
        assertEquals(List.of(), service.getManagedAnnouncements(FACULTY_ID, COURSE_ID));
    }

    @Test
    void courseTheCallerMayNotUseIsUnavailableAndNothingIsRead() {
        ApiException student = assertThrows(ApiException.class, () -> service.getPublishedAnnouncements(STUDENT_ID, 8L));
        ApiException faculty = assertThrows(ApiException.class, () -> service.getManagedAnnouncements(FACULTY_ID, 8L));
        // Each side's own access counts: a student cannot read the faculty list, and the reverse.
        assertThrows(ApiException.class, () -> service.getManagedAnnouncements(STUDENT_ID, COURSE_ID));
        assertThrows(ApiException.class, () -> service.getPublishedAnnouncements(FACULTY_ID, COURSE_ID));

        assertEquals(HttpStatus.NOT_FOUND, student.getStatus());
        assertEquals("Course not found or unavailable.", student.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, faculty.getStatus());
        verifyNoInteractions(announcements);
    }

    @Test
    void facultyGetDraftsAndPublishedButNotArchivedAnnouncementsWithTheirStatus() {
        when(announcements.findAllByLmsCourseIdAndStatusInOrderByCreatedAtDescIdDesc(COURSE_ID, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(
                        announcement(4, LmsContentStatus.DRAFT, null),
                        announcement(3, LmsContentStatus.ARCHIVED, "2026-10-15T09:00:00Z"),
                        announcement(2, LmsContentStatus.PUBLISHED, "2026-10-14T09:00:00Z")));

        List<LmsFacultyAnnouncementResponse> managed = service.getManagedAnnouncements(FACULTY_ID, COURSE_ID);

        assertEquals(List.of(4L, 2L), managed.stream().map(LmsFacultyAnnouncementResponse::id).toList());
        assertEquals(LmsContentStatus.DRAFT, managed.get(0).status());
        assertEquals(null, managed.get(0).publishedAt());
        assertEquals(Instant.parse("2026-10-14T09:00:00Z"), managed.get(1).publishedAt());
    }
}
