package com.stacc.backend.lms.assignment.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.Test;

/** The grade of one attempt and its rules. Timestamps are fixed UTC moments. */
class LmsSubmissionGradeTest {

    private static final Instant GRADED = Instant.parse("2026-10-21T04:00:00Z");
    private static final BigDecimal TWENTY = new BigDecimal("20");

    private final LmsCourse course = SubmissionFixtures.course(7);
    private final LmsSubmissionAttempt attempt = new LmsSubmissionAttempt(
            new LmsAssignmentSubmission(SubmissionFixtures.assignment(course, 5, LmsContentStatus.PUBLISHED),
                    SubmissionFixtures.membership(course, 12, 40).getCourseEnrollment()),
            1, "Answer", Instant.parse("2026-10-20T17:00:00Z"), SubmissionFixtures.DUE);
    private final FacultyProfile facultyA = SubmissionFixtures.faculty(course, 21, 61);
    private final FacultyProfile facultyB = SubmissionFixtures.faculty(course, 22, 62);

    private LmsSubmissionGrade grade(String marks, String feedback) {
        return new LmsSubmissionGrade(attempt, facultyA, new BigDecimal(marks), TWENTY, feedback, GRADED);
    }

    @Test
    void firstGradeRecordsTheMarksTheMaximumTheGraderAndTheTime() {
        LmsSubmissionGrade grade = grade("18", "Good solution.\nExplain question 4 further.");

        assertSame(attempt, grade.getAttempt());
        assertSame(facultyA, grade.getGradedByFacultyProfile());
        assertEquals(new BigDecimal("18.00"), grade.getMarksAwarded());
        assertEquals(new BigDecimal("20.00"), grade.getMaxMarksSnapshot());
        assertEquals("Good solution.\nExplain question 4 further.", grade.getFeedback());
        assertEquals(GRADED, grade.getGradedAt());
    }

    @Test
    void attemptGraderMarksMaximumAndTimeAreRequired() {
        BigDecimal ten = BigDecimal.TEN;
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionGrade(null, facultyA, ten, TWENTY, null, GRADED));
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionGrade(attempt, null, ten, TWENTY, null, GRADED));
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionGrade(attempt, facultyA, null, TWENTY, null, GRADED));
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionGrade(attempt, facultyA, ten, null, null, GRADED));
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionGrade(attempt, facultyA, ten, TWENTY, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new LmsSubmissionGrade(attempt, facultyA, BigDecimal.ZERO, BigDecimal.ZERO, null, GRADED));
    }

    @Test
    void marksGoFromZeroUpToTheMaximumWithAtMostTwoDecimals() {
        assertEquals(new BigDecimal("0.00"), grade("0", null).getMarksAwarded());
        assertEquals(new BigDecimal("12.50"), grade("12.5", null).getMarksAwarded());
        assertEquals(new BigDecimal("20.00"), grade("20", null).getMarksAwarded());
        for (String refused : new String[] {"-0.5", "20.01", "21", "12.555"}) {
            assertThrows(IllegalArgumentException.class, () -> grade(refused, null), refused);
        }
    }

    @Test
    void feedbackIsOptionalPlainTextKeptAsTyped() {
        assertNull(grade("10", null).getFeedback());
        assertNull(grade("10", "  \n ").getFeedback());
        assertEquals("<b>stays text</b>\n  indented", grade("10", "<b>stays text</b>\n  indented").getFeedback());
        assertThrows(IllegalArgumentException.class,
                () -> grade("10", "x".repeat(LmsSubmissionGrade.MAX_FEEDBACK_LENGTH + 1)));
    }

    @Test
    void revisionKeepsTheMaximumAndRecordsTheLatestGraderAndTime() {
        LmsSubmissionGrade grade = grade("18", "First look.");
        Instant later = Instant.parse("2026-10-22T09:15:00Z");

        grade.revise(facultyB, new BigDecimal("19"), null, later);

        assertEquals(new BigDecimal("19.00"), grade.getMarksAwarded());
        assertEquals(new BigDecimal("20.00"), grade.getMaxMarksSnapshot());
        assertNull(grade.getFeedback());
        assertSame(facultyB, grade.getGradedByFacultyProfile());
        assertEquals(later, grade.getGradedAt());
        assertThrows(IllegalArgumentException.class, () -> grade.revise(facultyB, new BigDecimal("25"), null, later));
        assertEquals(new BigDecimal("19.00"), grade.getMarksAwarded());
    }

    @Test
    void acceptsTellsWhetherMarksFitThisGrade() {
        LmsSubmissionGrade grade = grade("18", null);

        assertTrue(grade.accepts(new BigDecimal("0")));
        assertTrue(grade.accepts(new BigDecimal("20.00")));
        assertFalse(grade.accepts(new BigDecimal("20.5")));
        assertFalse(grade.accepts(new BigDecimal("-1")));
        assertFalse(grade.accepts(null));
    }
}
