package com.stacc.backend.academic.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.identity.faculty.FacultyProfile;
import org.junit.jupiter.api.Test;

class AttendanceSessionTest {

    private static final Instant HELD = Instant.parse("2026-10-19T04:30:00Z");

    private final CourseOffering offering = AttendanceFixtures.offering(17, "BCS301", "Data Structures");
    private final FacultyProfile faculty = AttendanceFixtures.faculty(21, 61);

    @Test
    void newSessionIsOpenAndNotFinalized() {
        AttendanceSession session = new AttendanceSession(offering, faculty, HELD, "  Arrays introduction  ");

        assertSame(offering, session.getCourseOffering());
        assertSame(faculty, session.getCreatedByFacultyProfile());
        assertEquals(HELD, session.getHeldAt());
        assertEquals("Arrays introduction", session.getTopic());
        assertEquals(AttendanceSessionStatus.OPEN, session.getStatus());
        assertNull(session.getFinalizedAt());
    }

    @Test
    void courseOfferingCreatorAndTimeAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new AttendanceSession(null, faculty, HELD, null));
        assertThrows(IllegalArgumentException.class, () -> new AttendanceSession(offering, null, HELD, null));
        assertThrows(IllegalArgumentException.class, () -> new AttendanceSession(offering, faculty, null, null));
    }

    @Test
    void topicIsOptionalAndLimitedTo500Characters() {
        assertNull(new AttendanceSession(offering, faculty, HELD, "   ").getTopic());
        assertEquals(500, new AttendanceSession(offering, faculty, HELD, "x".repeat(500)).getTopic().length());
        assertThrows(IllegalArgumentException.class, () -> new AttendanceSession(offering, faculty, HELD, "x".repeat(501)));
    }

    @Test
    void heldAtMustBeARealStorableMomentAndKeepsMicroseconds() {
        assertThrows(IllegalArgumentException.class,
                () -> new AttendanceSession(offering, faculty, Instant.parse("1999-12-31T23:59:59Z"), null));
        AttendanceSession session = new AttendanceSession(offering, faculty, Instant.parse("2026-10-19T04:30:00.123456789Z"), null);
        assertEquals(Instant.parse("2026-10-19T04:30:00.123456Z"), session.getHeldAt());
    }

    @Test
    void finalizingSetsTheStatusAndTimeOnce() {
        AttendanceSession session = new AttendanceSession(offering, faculty, HELD, null);

        session.markFinalized(Instant.parse("2026-10-19T06:00:00.987654321Z"));
        session.markFinalized(Instant.parse("2026-10-25T09:00:00Z"));

        assertTrue(session.isFinalized());
        assertEquals(AttendanceSessionStatus.FINALIZED, session.getStatus());
        assertEquals(Instant.parse("2026-10-19T06:00:00.987654Z"), session.getFinalizedAt());
    }

    @Test
    void anOpenSessionCanBeRescheduledButAFinalizedOneCannot() {
        AttendanceSession session = new AttendanceSession(offering, faculty, HELD, "Old");
        Instant moved = Instant.parse("2026-10-20T04:30:00Z");

        session.reschedule(moved, "Unit 2 revision");
        assertEquals(moved, session.getHeldAt());
        assertEquals("Unit 2 revision", session.getTopic());

        session.markFinalized(Instant.parse("2026-10-20T06:00:00Z"));
        assertThrows(IllegalStateException.class, () -> session.reschedule(HELD, "Changed"));
        assertEquals(moved, session.getHeldAt());
    }
}
