package com.stacc.backend.lms.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.Test;

class LmsAnnouncementTest {

    private static final Instant FIRST = Instant.parse("2026-10-14T09:30:00Z");
    private static final Instant LATER = Instant.parse("2026-10-15T11:00:00Z");

    private final LmsCourse course = ContentFixtures.lmsCourse(7);

    @Test
    void newAnnouncementIsAnUnpublishedDraftOfItsCourse() {
        LmsAnnouncement announcement = new LmsAnnouncement(course, "  Exam schedule  ", "Line one\nLine two");

        assertSame(course, announcement.getLmsCourse());
        assertEquals("Exam schedule", announcement.getTitle());
        assertEquals("Line one\nLine two", announcement.getMessage());
        assertEquals(LmsContentStatus.DRAFT, announcement.getStatus());
        assertNull(announcement.getPublishedAt());
    }

    @Test
    void courseTitleAndMessageAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new LmsAnnouncement(null, "Title", "Message"));
        assertThrows(IllegalArgumentException.class, () -> new LmsAnnouncement(course, " ", "Message"));
        assertThrows(IllegalArgumentException.class, () -> new LmsAnnouncement(course, "Title", null));
        assertThrows(IllegalArgumentException.class, () -> new LmsAnnouncement(course, "Title", "  \n "));
        assertThrows(IllegalArgumentException.class, () -> new LmsAnnouncement(course, "x".repeat(201), "Message"));
        assertThrows(IllegalArgumentException.class, () -> new LmsAnnouncement(course, "Title", "x".repeat(10_001)));
    }

    @Test
    void messageIsKeptAsPlainText() {
        String message = "<b>Bring</b> a calculator.\n<script>alert(1)</script>";

        assertEquals(message, new LmsAnnouncement(course, "Title", message).getMessage());
    }

    @Test
    void firstPublicationSetsTheTimeAndPublishingAgainChangesNothing() {
        LmsAnnouncement announcement = new LmsAnnouncement(course, "Title", "Message");

        announcement.publish(FIRST);
        announcement.publish(LATER);

        assertEquals(LmsContentStatus.PUBLISHED, announcement.getStatus());
        assertEquals(FIRST, announcement.getPublishedAt());
    }

    @Test
    void publishedAnnouncementCanBeCorrectedAndKeepsItsPublicationTime() {
        LmsAnnouncement announcement = new LmsAnnouncement(course, "Title", "Message");
        announcement.publish(FIRST);

        announcement.edit("Corrected title", "Corrected message");

        assertEquals("Corrected title", announcement.getTitle());
        assertEquals("Corrected message", announcement.getMessage());
        assertEquals(LmsContentStatus.PUBLISHED, announcement.getStatus());
        assertEquals(FIRST, announcement.getPublishedAt());
    }

    @Test
    void archivingKeepsThePublicationTimeAndEndsAllChanges() {
        LmsAnnouncement announcement = new LmsAnnouncement(course, "Title", "Message");
        announcement.publish(FIRST);

        announcement.archive();
        announcement.archive();

        assertEquals(LmsContentStatus.ARCHIVED, announcement.getStatus());
        assertEquals(FIRST, announcement.getPublishedAt());
        assertThrows(IllegalStateException.class, () -> announcement.publish(LATER));
        assertThrows(IllegalStateException.class, () -> announcement.edit("New", "New"));
        assertEquals("Title", announcement.getTitle());
        assertEquals(FIRST, announcement.getPublishedAt());
    }

    @Test
    void draftCanBeArchivedWithoutEverBeingPublished() {
        LmsAnnouncement announcement = new LmsAnnouncement(course, "Title", "Message");

        announcement.archive();

        assertEquals(LmsContentStatus.ARCHIVED, announcement.getStatus());
        assertNull(announcement.getPublishedAt());
    }
}
