package com.stacc.backend.lms.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.Test;

/** Fixed timestamps only: nothing here depends on the clock or the machine's time zone. */
class LmsAssignmentTest {

    private static final Instant DUE = Instant.parse("2026-10-20T18:29:00Z");
    private static final Instant FIRST_PUBLISH = Instant.parse("2026-10-14T09:30:00Z");

    private final LmsCourse course = ContentFixtures.lmsCourse(7);

    private LmsAssignment assignment() {
        return new LmsAssignment(course, "Arrays practice", "Solve problems 1 to 5.", DUE, new BigDecimal("20"));
    }

    @Test
    void newAssignmentIsAnUnpublishedDraftOfItsCourse() {
        LmsAssignment assignment = new LmsAssignment(course, "  Arrays practice  ", "Line one\nLine two", DUE, new BigDecimal("12.5"));

        assertSame(course, assignment.getLmsCourse());
        assertEquals("Arrays practice", assignment.getTitle());
        assertEquals("Line one\nLine two", assignment.getInstructions());
        assertEquals(DUE, assignment.getDueAt());
        assertEquals(new BigDecimal("12.50"), assignment.getMaxMarks());
        assertEquals(LmsContentStatus.DRAFT, assignment.getStatus());
        assertNull(assignment.getPublishedAt());
    }

    @Test
    void courseTitleInstructionsDeadlineAndMarksAreRequired() {
        BigDecimal marks = new BigDecimal("20");
        assertThrows(IllegalArgumentException.class, () -> new LmsAssignment(null, "Title", "Text", DUE, marks));
        assertThrows(IllegalArgumentException.class, () -> new LmsAssignment(course, " ", "Text", DUE, marks));
        assertThrows(IllegalArgumentException.class, () -> new LmsAssignment(course, null, "Text", DUE, marks));
        assertThrows(IllegalArgumentException.class, () -> new LmsAssignment(course, "Title", "  ", DUE, marks));
        assertThrows(IllegalArgumentException.class, () -> new LmsAssignment(course, "Title", null, DUE, marks));
        assertThrows(IllegalArgumentException.class, () -> new LmsAssignment(course, "Title", "Text", null, marks));
        assertThrows(IllegalArgumentException.class, () -> new LmsAssignment(course, "Title", "Text", DUE, null));
        assertThrows(IllegalArgumentException.class,
                () -> new LmsAssignment(course, "x".repeat(LmsAssignment.MAX_TITLE_LENGTH + 1), "Text", DUE, marks));
        assertThrows(IllegalArgumentException.class,
                () -> new LmsAssignment(course, "Title", "x".repeat(LmsAssignment.MAX_INSTRUCTIONS_LENGTH + 1), DUE, marks));
    }

    @Test
    void maximumMarksMustBePositiveAndStorable() {
        for (String refused : new String[] {"0", "0.00", "-5", "12.555", "1000000"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new LmsAssignment(course, "Title", "Text", DUE, new BigDecimal(refused)), refused);
        }
        assertEquals(new BigDecimal("1.00"), new LmsAssignment(course, "Title", "Text", DUE, BigDecimal.ONE).getMaxMarks());
        assertEquals(new BigDecimal("0.25"), new LmsAssignment(course, "Title", "Text", DUE, new BigDecimal("0.25")).getMaxMarks());
        assertEquals(LmsAssignment.MAX_MARKS,
                new LmsAssignment(course, "Title", "Text", DUE, LmsAssignment.MAX_MARKS).getMaxMarks());
    }

    @Test
    void deadlinesOutsideTheSupportedRangeAreRefused() {
        assertTrue(LmsAssignment.isSupportedDueAt(LmsAssignment.EARLIEST_DUE_AT));
        assertTrue(LmsAssignment.isSupportedDueAt(LmsAssignment.LATEST_DUE_AT));
        assertFalse(LmsAssignment.isSupportedDueAt(Instant.parse("1999-12-31T23:59:59Z")));
        assertFalse(LmsAssignment.isSupportedDueAt(Instant.parse("+20000-01-01T00:00:00Z")));
        assertFalse(LmsAssignment.isSupportedDueAt(null));
        assertThrows(IllegalArgumentException.class, () -> new LmsAssignment(
                course, "Title", "Text", Instant.parse("1970-01-01T00:00:00Z"), BigDecimal.TEN));
    }

    @Test
    void publishingRecordsTheFirstTimeAndRepeatingItChangesNothing() {
        LmsAssignment assignment = assignment();

        assignment.publish(FIRST_PUBLISH);
        assignment.publish(Instant.parse("2026-10-16T12:00:00Z"));

        assertEquals(LmsContentStatus.PUBLISHED, assignment.getStatus());
        assertEquals(FIRST_PUBLISH, assignment.getPublishedAt());
        assertThrows(IllegalArgumentException.class, () -> assignment().publish(null));
    }

    @Test
    void publishedAssignmentCanBeEditedAndKeepsItsPublicationTime() {
        LmsAssignment assignment = assignment();
        assignment.publish(FIRST_PUBLISH);
        Instant moved = Instant.parse("2026-10-22T18:29:00Z");

        assignment.edit("Arrays practice (corrected)", "Solve problems 1 to 6.", moved, new BigDecimal("25"));

        assertEquals("Arrays practice (corrected)", assignment.getTitle());
        assertEquals("Solve problems 1 to 6.", assignment.getInstructions());
        assertEquals(moved, assignment.getDueAt());
        assertEquals(new BigDecimal("25.00"), assignment.getMaxMarks());
        assertEquals(LmsContentStatus.PUBLISHED, assignment.getStatus());
        assertEquals(FIRST_PUBLISH, assignment.getPublishedAt());
    }

    @Test
    void refusedEditChangesNothing() {
        LmsAssignment assignment = assignment();

        assertThrows(IllegalArgumentException.class,
                () -> assignment.edit("New title", "New text", Instant.parse("2026-11-01T00:00:00Z"), BigDecimal.ZERO));

        assertEquals("Arrays practice", assignment.getTitle());
        assertEquals(DUE, assignment.getDueAt());
        assertEquals(new BigDecimal("20.00"), assignment.getMaxMarks());
    }

    @Test
    void archivingKeepsThePublicationTimeAndCanBeRepeated() {
        LmsAssignment assignment = assignment();
        assignment.publish(FIRST_PUBLISH);

        assignment.archive();
        assignment.archive();

        assertTrue(assignment.isArchived());
        assertEquals(FIRST_PUBLISH, assignment.getPublishedAt());
    }

    @Test
    void draftCanBeArchivedDirectly() {
        LmsAssignment assignment = assignment();

        assignment.archive();

        assertEquals(LmsContentStatus.ARCHIVED, assignment.getStatus());
        assertNull(assignment.getPublishedAt());
    }

    @Test
    void archivedAssignmentCanNeitherBeEditedNorPublishedAgain() {
        LmsAssignment assignment = assignment();
        assignment.archive();

        assertThrows(IllegalStateException.class,
                () -> assignment.edit("Back", "Back", DUE, BigDecimal.TEN));
        assertThrows(IllegalStateException.class, () -> assignment.publish(FIRST_PUBLISH));
        assertEquals(LmsContentStatus.ARCHIVED, assignment.getStatus());
        assertEquals("Arrays practice", assignment.getTitle());
    }
}
