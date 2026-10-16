package com.stacc.backend.lms.assignment.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.Test;

/** The three submission records and their rules. Timestamps are fixed UTC moments. */
class LmsSubmissionModelTest {

    private final LmsCourse course = SubmissionFixtures.course(7);
    private final LmsAssignment assignment = SubmissionFixtures.assignment(course, 5, LmsContentStatus.PUBLISHED);
    private final CourseEnrollment enrollment = SubmissionFixtures.membership(course, 12, 40).getCourseEnrollment();
    private final LmsAssignmentSubmission submission = new LmsAssignmentSubmission(assignment, enrollment);

    private LmsSubmissionAttempt attempt(String submittedAt) {
        return new LmsSubmissionAttempt(submission, 1, "Answer", Instant.parse(submittedAt), SubmissionFixtures.DUE);
    }

    @Test
    void submissionBelongsToOneAssignmentAndOneCourseEnrollmentAndStartsWithNoAttempts() {
        assertSame(assignment, submission.getAssignment());
        assertSame(enrollment, submission.getCourseEnrollment());
        assertEquals(0, submission.getLatestAttemptNumber());
        assertThrows(IllegalArgumentException.class, () -> new LmsAssignmentSubmission(null, enrollment));
        assertThrows(IllegalArgumentException.class, () -> new LmsAssignmentSubmission(assignment, null));
    }

    @Test
    void attemptNumbersCountUpFromOne() {
        assertEquals(1, submission.takeNextAttemptNumber());
        assertEquals(2, submission.takeNextAttemptNumber());
        assertEquals(3, submission.takeNextAttemptNumber());
        assertEquals(3, submission.getLatestAttemptNumber());
    }

    @Test
    void attemptNeedsItsSubmissionAPositiveNumberAndBothMoments() {
        Instant at = Instant.parse("2026-10-20T17:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionAttempt(null, 1, "x", at, at));
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionAttempt(submission, 0, "x", at, at));
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionAttempt(submission, -1, "x", at, at));
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionAttempt(submission, 1, "x", null, at));
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionAttempt(submission, 1, "x", at, null));
    }

    @Test
    void textIsKeptExactlyAsTypedAndBlankTextCountsAsNone() {
        Instant at = Instant.parse("2026-10-20T17:00:00Z");
        String typed = "  for (int i = 0; i < n; i++) {\n      sum += a[i];\n  }\n<b>not html</b>";

        assertEquals(typed, new LmsSubmissionAttempt(submission, 1, typed, at, at).getTextContent());
        assertNull(new LmsSubmissionAttempt(submission, 1, " \n\t ", at, at).getTextContent());
        assertNull(new LmsSubmissionAttempt(submission, 1, null, at, at).getTextContent());
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionAttempt(
                submission, 1, "x".repeat(LmsSubmissionAttempt.MAX_TEXT_LENGTH + 1), at, at));
    }

    @Test
    void lateIsDecidedByTheDeadlineKeptWithTheAttempt() {
        assertFalse(attempt("2026-10-20T17:00:00Z").isLate());
        assertFalse(attempt("2026-10-20T18:00:00Z").isLate());
        assertTrue(attempt("2026-10-20T19:00:00Z").isLate());
    }

    @Test
    void movingTheAssignmentDeadlineLaterDoesNotChangeAnEarlierAttempt() {
        LmsSubmissionAttempt lateAttempt = attempt("2026-10-20T19:00:00Z");

        assignment.edit(assignment.getTitle(), assignment.getInstructions(), Instant.parse("2026-10-22T18:00:00Z"),
                assignment.getMaxMarks());

        assertEquals(SubmissionFixtures.DUE, lateAttempt.getDueAtSnapshot());
        assertTrue(lateAttempt.isLate());
    }

    @Test
    void attachmentKeepsSafeDetailsOfOneFileOfOneAttempt() {
        LmsSubmissionAttempt attempt = attempt("2026-10-20T17:00:00Z");
        LmsSubmissionAttachment attachment = new LmsSubmissionAttachment(
                attempt, "lms/submissions/assignments/5/enrollments/40/key", "solution.pdf", "application/pdf", 1024);

        assertSame(attempt, attachment.getAttempt());
        assertEquals("solution.pdf", attachment.getOriginalFileName());
        assertEquals(1024, attachment.getSizeBytes());
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionAttachment(null, "key", "a.pdf", "application/pdf", 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionAttachment(attempt, " ", "a.pdf", "application/pdf", 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionAttachment(attempt, "key", " ", "application/pdf", 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsSubmissionAttachment(attempt, "key", "a.pdf", "application/pdf", -1));
    }

    @Test
    void attemptsAndAttachmentsCannotBeChangedOnceWritten() {
        for (Class<?> type : List.of(LmsSubmissionAttempt.class, LmsSubmissionAttachment.class)) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Id.class)) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                boolean updatable = column != null ? column.updatable() : joinColumn == null || joinColumn.updatable();
                assertFalse(updatable, type.getSimpleName() + "." + field.getName() + " must not be updatable");
            }
            List<String> changers = Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .map(Method::getName)
                    .filter(name -> name.startsWith("set") || name.startsWith("edit") || name.startsWith("update")
                            || name.startsWith("change") || name.startsWith("delete"))
                    .toList();
            assertEquals(List.of(), changers, type.getSimpleName() + " has no methods that change it");
        }
    }
}
