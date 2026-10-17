package com.stacc.backend.academic.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.identity.faculty.FacultyProfile;
import org.junit.jupiter.api.Test;

class CourseResultTest {

    private static final Instant FIRST = Instant.parse("2026-11-20T10:00:00.123456789Z");
    private static final Instant LATER = Instant.parse("2026-11-25T09:00:00Z");

    private final CourseOffering offering = AttendanceFixtures.offering(17, "BCS301", "Data Structures");
    private final CourseEnrollment enrollment = AttendanceFixtures.enrollment(offering, 11, 40, "2408400100011");
    private final FacultyProfile facultyA = AttendanceFixtures.faculty(21, 61);
    private final FacultyProfile facultyC = AttendanceFixtures.faculty(23, 63);

    private static BigDecimal marks(String value) {
        return new BigDecimal(value);
    }

    private CourseResult result(String obtained, String max) {
        return new CourseResult(enrollment, facultyA, marks(obtained), marks(max), null, null);
    }

    @Test
    void newResultIsADraftWithNoPublicationTime() {
        CourseResult result = new CourseResult(enrollment, facultyA, marks("82"), marks("100"), "A", "Good performance");

        assertSame(enrollment, result.getCourseEnrollment());
        assertSame(facultyA, result.getRecordedByFacultyProfile());
        assertEquals(marks("82.00"), result.getMarksObtained());
        assertEquals(marks("100.00"), result.getMaxMarks());
        assertEquals("A", result.getGradeCode());
        assertEquals("Good performance", result.getRemarks());
        assertEquals(CourseResultStatus.DRAFT, result.getStatus());
        assertNull(result.getPublishedAt());
    }

    @Test
    void enrollmentRecorderAndBothMarksAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new CourseResult(null, facultyA, marks("1"), marks("10"), null, null));
        assertThrows(IllegalArgumentException.class, () -> new CourseResult(enrollment, null, marks("1"), marks("10"), null, null));
        assertThrows(IllegalArgumentException.class, () -> new CourseResult(enrollment, facultyA, null, marks("10"), null, null));
        assertThrows(IllegalArgumentException.class, () -> new CourseResult(enrollment, facultyA, marks("1"), null, null, null));
    }

    @Test
    void marksFromZeroUpToTheMaximumAreValidIncludingDecimals() {
        assertEquals(marks("0.00"), result("0", "100").getMarksObtained());
        assertEquals(marks("100.00"), result("100", "100").getMarksObtained());
        CourseResult decimal = result("12.5", "20");
        assertEquals(marks("12.50"), decimal.getMarksObtained());
        assertEquals(marks("20.00"), decimal.getMaxMarks());
    }

    @Test
    void negativeMarksAMaximumNotAboveZeroAndMarksAboveTheMaximumAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> result("-1", "100"));
        assertThrows(IllegalArgumentException.class, () -> result("0", "0"));
        assertThrows(IllegalArgumentException.class, () -> result("0", "-5"));
        assertThrows(IllegalArgumentException.class, () -> result("100.01", "100"));
        assertThrows(IllegalArgumentException.class, () -> result("10.555", "20"));
        assertThrows(IllegalArgumentException.class, () -> result("1", "1000000"));
    }

    @Test
    void gradeCodeIsOptionalAndKeptAsEnteredApartFromSpaces() {
        assertNull(new CourseResult(enrollment, facultyA, marks("82"), marks("100"), null, null).getGradeCode());
        assertNull(new CourseResult(enrollment, facultyA, marks("82"), marks("100"), "   ", null).getGradeCode());
        assertEquals("A+", new CourseResult(enrollment, facultyA, marks("82"), marks("100"), " A+ ", null).getGradeCode());
        assertEquals("b+", new CourseResult(enrollment, facultyA, marks("82"), marks("100"), "b+", null).getGradeCode());
        assertEquals("Pass with merit", CourseResult.gradeCodeOrNull("Pass   with\tmerit"));
        assertThrows(IllegalArgumentException.class, () -> CourseResult.gradeCodeOrNull("x".repeat(21)));
    }

    @Test
    void remarksAreOptionalPlainTextUpTo2000Characters() {
        assertNull(new CourseResult(enrollment, facultyA, marks("82"), marks("100"), null, "  ").getRemarks());
        assertEquals("Line one\nLine two", CourseResult.remarksOrNull("  Line one\nLine two  "));
        assertEquals(2000, CourseResult.remarksOrNull("x".repeat(2000)).length());
        assertThrows(IllegalArgumentException.class, () -> CourseResult.remarksOrNull("x".repeat(2001)));
    }

    @Test
    void publishingSetsTheStatusAndTimeOnce() {
        CourseResult result = result("82", "100");

        result.publish(FIRST);
        result.publish(LATER);

        assertTrue(result.isPublished());
        assertEquals(Instant.parse("2026-11-20T10:00:00.123456Z"), result.getPublishedAt());
    }

    @Test
    void aCorrectionOfAPublishedResultStaysPublishedWithItsFirstPublicationTime() {
        CourseResult result = result("82", "100");
        result.publish(FIRST);

        result.record(facultyC, marks("84"), marks("100"), "A", "Rechecked");

        assertEquals(CourseResultStatus.PUBLISHED, result.getStatus());
        assertEquals(Instant.parse("2026-11-20T10:00:00.123456Z"), result.getPublishedAt());
        assertEquals(marks("84.00"), result.getMarksObtained());
        assertSame(facultyC, result.getRecordedByFacultyProfile());
    }

    @Test
    void anInvalidCorrectionChangesNothing() {
        CourseResult result = result("82", "100");

        assertThrows(IllegalArgumentException.class, () -> result.record(facultyC, marks("120"), marks("100"), "A", null));

        assertEquals(marks("82.00"), result.getMarksObtained());
        assertSame(facultyA, result.getRecordedByFacultyProfile());
        assertNull(result.getGradeCode());
    }
}
