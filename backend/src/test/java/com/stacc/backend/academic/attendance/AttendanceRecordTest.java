package com.stacc.backend.academic.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.identity.faculty.FacultyProfile;
import org.junit.jupiter.api.Test;

class AttendanceRecordTest {

    private static final Instant FIRST = Instant.parse("2026-10-19T05:00:00.123456789Z");
    private static final Instant LATER = Instant.parse("2026-10-20T09:00:00Z");

    private final CourseOffering offering = AttendanceFixtures.offering(17, "BCS301", "Data Structures");
    private final FacultyProfile facultyA = AttendanceFixtures.faculty(21, 61);
    private final FacultyProfile facultyC = AttendanceFixtures.faculty(23, 63);
    private final AttendanceSession session = AttendanceFixtures.session(offering, facultyA, 8, "2026-10-19T04:30:00Z");
    private final CourseEnrollment enrollment = AttendanceFixtures.enrollment(offering, 11, 40, "2408400100011");

    @Test
    void newRecordIsUnmarkedNeverAbsent() {
        AttendanceRecord record = new AttendanceRecord(session, enrollment);

        assertSame(session, record.getAttendanceSession());
        assertSame(enrollment, record.getCourseEnrollment());
        assertEquals(AttendanceStatus.UNMARKED, record.getStatus());
        assertFalse(record.isMarked());
        assertNull(record.getMarkedByFacultyProfile());
        assertNull(record.getMarkedAt());
    }

    @Test
    void sessionAndEnrollmentAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new AttendanceRecord(null, enrollment));
        assertThrows(IllegalArgumentException.class, () -> new AttendanceRecord(session, null));
    }

    @Test
    void markingPresentOrAbsentRecordsTheFacultyAndTime() {
        AttendanceRecord record = new AttendanceRecord(session, enrollment);

        assertTrue(record.mark(AttendanceStatus.PRESENT, facultyA, FIRST));
        assertEquals(AttendanceStatus.PRESENT, record.getStatus());
        assertSame(facultyA, record.getMarkedByFacultyProfile());
        assertEquals(Instant.parse("2026-10-19T05:00:00.123456Z"), record.getMarkedAt());

        assertTrue(record.mark(AttendanceStatus.ABSENT, facultyC, LATER));
        assertEquals(AttendanceStatus.ABSENT, record.getStatus());
        assertSame(facultyC, record.getMarkedByFacultyProfile());
        assertEquals(LATER, record.getMarkedAt());
    }

    @Test
    void savingTheSameStatusAgainKeepsTheLastRealChange() {
        AttendanceRecord record = new AttendanceRecord(session, enrollment);
        record.mark(AttendanceStatus.PRESENT, facultyA, FIRST);

        assertFalse(record.mark(AttendanceStatus.PRESENT, facultyC, LATER));
        assertSame(facultyA, record.getMarkedByFacultyProfile());
        assertEquals(Instant.parse("2026-10-19T05:00:00.123456Z"), record.getMarkedAt());
    }

    @Test
    void anOpenSessionsRecordCanGoBackToUnmarked() {
        AttendanceRecord record = new AttendanceRecord(session, enrollment);
        record.mark(AttendanceStatus.ABSENT, facultyA, FIRST);

        assertTrue(record.mark(AttendanceStatus.UNMARKED, facultyA, LATER));
        assertEquals(AttendanceStatus.UNMARKED, record.getStatus());
        assertNull(record.getMarkedByFacultyProfile());
        assertNull(record.getMarkedAt());
    }

    @Test
    void aFinalizedSessionsRecordCanOnlyBeCorrectedBetweenPresentAndAbsent() {
        AttendanceRecord record = new AttendanceRecord(session, enrollment);
        record.mark(AttendanceStatus.PRESENT, facultyA, FIRST);
        session.markFinalized(FIRST);

        assertThrows(IllegalStateException.class, () -> record.mark(AttendanceStatus.UNMARKED, facultyC, LATER));
        assertEquals(AttendanceStatus.PRESENT, record.getStatus());

        record.mark(AttendanceStatus.ABSENT, facultyC, LATER);
        assertEquals(AttendanceStatus.ABSENT, record.getStatus());
        assertSame(facultyC, record.getMarkedByFacultyProfile());
    }

    @Test
    void markingNeedsAStatusAndAFacultyMember() {
        AttendanceRecord record = new AttendanceRecord(session, enrollment);

        assertThrows(IllegalArgumentException.class, () -> record.mark(null, facultyA, FIRST));
        assertThrows(IllegalArgumentException.class, () -> record.mark(AttendanceStatus.PRESENT, null, FIRST));
    }
}
