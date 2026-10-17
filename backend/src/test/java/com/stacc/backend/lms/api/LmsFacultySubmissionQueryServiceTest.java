package com.stacc.backend.lms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttachment;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttachmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttemptRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGradeRepository;
import com.stacc.backend.lms.assignment.submission.SubmissionFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.membership.LmsStudentMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Faculty A (account 21) may review LMS course 7, whose published assignment 5 has 20 marks. The access
 * service and the repositories are stand-ins: the access rules themselves are tested with
 * LmsFacultyAccessService and the grading service. Timestamps are fixed UTC moments.
 */
class LmsFacultySubmissionQueryServiceTest {

    private static final Long FACULTY_A = 21L;
    private static final Long FACULTY_B = 22L;
    private static final Instant DUE = Instant.parse("2026-10-20T18:00:00Z");

    private final LmsAssignmentRepository assignments = mock(LmsAssignmentRepository.class);
    private final LmsAssignmentSubmissionRepository submissions = mock(LmsAssignmentSubmissionRepository.class);
    private final LmsSubmissionAttemptRepository attempts = mock(LmsSubmissionAttemptRepository.class);
    private final LmsSubmissionAttachmentRepository attachments = mock(LmsSubmissionAttachmentRepository.class);
    private final LmsSubmissionGradeRepository grades = mock(LmsSubmissionGradeRepository.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final LmsFacultySubmissionQueryService service = new LmsFacultySubmissionQueryService(
            assignments, submissions, attempts, attachments, grades, facultyAccess, storage);

    private final LmsCourse course = SubmissionFixtures.course(7);
    private final LmsAssignment assignment = SubmissionFixtures.assignment(course, 5, LmsContentStatus.PUBLISHED);
    private final FacultyProfile grader = mock(FacultyProfile.class);

    /** A submission by the student with this login ID, with its attempts numbered 1 to the count. */
    private LmsAssignmentSubmission submission(LmsAssignment of, long id, String studentLoginId, int attemptCount) {
        LmsStudentMembership membership = SubmissionFixtures.membership(of.getLmsCourse(), id + 1000, id + 40, studentLoginId);
        LmsAssignmentSubmission created = new LmsAssignmentSubmission(of, membership.getCourseEnrollment());
        ReflectionTestUtils.setField(created, "id", id);
        ReflectionTestUtils.setField(created, "latestAttemptNumber", attemptCount);
        return created;
    }

    private static LmsSubmissionAttempt attempt(LmsAssignmentSubmission of, long id, int number, String submittedAt) {
        LmsSubmissionAttempt created = new LmsSubmissionAttempt(of, number, "Work " + number, Instant.parse(submittedAt), DUE);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private LmsSubmissionGrade grade(LmsSubmissionAttempt attempt, String marks) {
        return new LmsSubmissionGrade(attempt, grader, new BigDecimal(marks), new BigDecimal("20"), "Feedback", DUE);
    }

    private static LmsSubmissionAttemptRepository.AttemptCount count(long submissionId, long attemptCount) {
        return new LmsSubmissionAttemptRepository.AttemptCount() {
            @Override
            public Long getSubmissionId() {
                return submissionId;
            }

            @Override
            public long getAttemptCount() {
                return attemptCount;
            }
        };
    }

    private static void assertUnavailable(String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @BeforeEach
    void facultyAWhoTeachesCourseSeven() {
        when(assignments.findById(5L)).thenReturn(Optional.of(assignment));
        when(facultyAccess.canAccessCourse(FACULTY_A, 7L)).thenReturn(true);
    }

    @Test
    void listDescribesEachStudentsLatestAttemptNewestFirst() {
        LmsAssignmentSubmission early = submission(assignment, 12, "2408400100011", 2);
        LmsAssignmentSubmission late = submission(assignment, 13, "2408400100012", 1);
        LmsSubmissionAttempt earlyFirst = attempt(early, 101, 1, "2026-10-19T10:00:00Z");
        LmsSubmissionAttempt earlyLatest = attempt(early, 102, 2, "2026-10-20T10:00:00Z");
        LmsSubmissionAttempt lateLatest = attempt(late, 103, 1, "2026-10-20T19:00:00Z");
        when(submissions.findAllWithStudentByAssignmentId(5L)).thenReturn(List.of(early, late));
        when(attempts.findLatestBySubmissionIdIn(List.of(12L, 13L))).thenReturn(List.of(earlyLatest, lateLatest));
        when(attempts.countBySubmissionIdIn(List.of(12L, 13L))).thenReturn(List.of(count(12, 2), count(13, 1)));
        // Only the older attempt of the first student is graded; its latest attempt is not.
        when(grades.findAllByAttemptIdIn(List.of(102L, 103L))).thenReturn(List.of(grade(lateLatest, "15")));
        assertEquals(1, earlyFirst.getAttemptNumber());

        List<LmsFacultySubmissionSummaryResponse> listed = service.listSubmissions(FACULTY_A, 5L);

        assertEquals(List.of(13L, 12L), listed.stream().map(LmsFacultySubmissionSummaryResponse::submissionId).toList());
        LmsFacultySubmissionSummaryResponse newest = listed.get(0);
        assertEquals("2408400100012", newest.studentLoginId());
        assertTrue(newest.latestLate());
        assertTrue(newest.latestGraded());
        assertEquals(new BigDecimal("15.00"), newest.latestMarksAwarded());
        assertEquals(new BigDecimal("20.00"), newest.latestMaxMarksSnapshot());
        LmsFacultySubmissionSummaryResponse older = listed.get(1);
        assertEquals(2, older.latestAttemptNumber());
        assertEquals(2, older.attemptCount());
        assertFalse(older.latestLate());
        assertFalse(older.latestGraded());
        assertNull(older.latestMarksAwarded());
    }

    @Test
    void olderGradedAttemptDoesNotMakeTheLatestAttemptLookGraded() {
        LmsAssignmentSubmission resubmitted = submission(assignment, 12, "2408400100011", 2);
        LmsSubmissionAttempt latest = attempt(resubmitted, 102, 2, "2026-10-20T10:00:00Z");
        when(submissions.findAllWithStudentByAssignmentId(5L)).thenReturn(List.of(resubmitted));
        when(attempts.findLatestBySubmissionIdIn(List.of(12L))).thenReturn(List.of(latest));
        when(attempts.countBySubmissionIdIn(List.of(12L))).thenReturn(List.of(count(12, 2)));
        when(grades.findAllByAttemptIdIn(List.of(102L))).thenReturn(List.of());

        LmsFacultySubmissionSummaryResponse row = service.listSubmissions(FACULTY_A, 5L).get(0);

        assertFalse(row.latestGraded());
        assertNull(row.latestMaxMarksSnapshot());
    }

    @Test
    void assignmentWithoutSubmissionsGivesAnEmptyList() {
        when(submissions.findAllWithStudentByAssignmentId(5L)).thenReturn(List.of());

        assertEquals(List.of(), service.listSubmissions(FACULTY_A, 5L));
        verifyNoInteractions(attempts, grades);
    }

    @Test
    void assignmentsTheCallerMayNotReviewAllLookTheSame() {
        when(assignments.findById(6L)).thenReturn(Optional.of(SubmissionFixtures.assignment(course, 6, LmsContentStatus.DRAFT)));
        when(assignments.findById(9L)).thenReturn(Optional.of(SubmissionFixtures.assignment(course, 9, LmsContentStatus.ARCHIVED)));

        for (Executable action : List.<Executable>of(
                () -> service.listSubmissions(FACULTY_B, 5L),
                () -> service.listSubmissions(FACULTY_A, 6L),
                () -> service.listSubmissions(FACULTY_A, 9L),
                () -> service.listSubmissions(FACULTY_A, 999L),
                () -> service.submissionDetail(FACULTY_B, 5L, 12L))) {
            assertUnavailable(LmsFacultySubmissionQueryService.ASSIGNMENT_UNAVAILABLE, action);
        }
        verifyNoInteractions(submissions);
    }

    @Test
    void detailListsEveryAttemptNewestFirstWithItsFilesAndOwnGrade() {
        LmsAssignmentSubmission own = submission(assignment, 12, "2408400100011", 2);
        LmsSubmissionAttempt firstAttempt = attempt(own, 101, 1, "2026-10-19T10:00:00Z");
        LmsSubmissionAttempt latest = attempt(own, 102, 2, "2026-10-20T19:00:00Z");
        LmsSubmissionAttachment file = new LmsSubmissionAttachment(latest, "lms/submissions/secret", "answer.pdf", "application/pdf", 9);
        ReflectionTestUtils.setField(file, "id", 31L);
        when(submissions.findWithStudentById(12L)).thenReturn(Optional.of(own));
        when(attempts.findAllBySubmissionIdOrderByAttemptNumberDesc(12L)).thenReturn(List.of(latest, firstAttempt));
        when(attachments.findAllByAttemptIdInOrderByIdAsc(List.of(102L, 101L))).thenReturn(List.of(file));
        when(grades.findAllByAttemptIdIn(List.of(102L, 101L))).thenReturn(List.of(grade(firstAttempt, "14")));

        LmsFacultySubmissionDetailResponse detail = service.submissionDetail(FACULTY_A, 5L, 12L);

        assertEquals("2408400100011", detail.studentLoginId());
        assertEquals(new BigDecimal("20.00"), detail.maxMarks());
        assertEquals(List.of(2, 1), detail.attempts().stream().map(LmsFacultySubmissionAttemptResponse::attemptNumber).toList());
        LmsFacultySubmissionAttemptResponse newest = detail.attempts().get(0);
        assertTrue(newest.late());
        assertEquals(DUE, newest.dueAtSnapshot());
        assertNull(newest.grade());
        assertEquals(List.of(new LmsSubmissionAttachmentResponse(31L, "answer.pdf", "application/pdf", 9)), newest.attachments());
        assertEquals(new BigDecimal("14.00"), detail.attempts().get(1).grade().marksAwarded());
    }

    @Test
    void submissionOfAnotherAssignmentOrMissingLooksTheSame() {
        LmsAssignment otherAssignment = SubmissionFixtures.assignment(course, 8, LmsContentStatus.PUBLISHED);
        when(submissions.findWithStudentById(14L)).thenReturn(Optional.of(submission(otherAssignment, 14, "2408400100013", 1)));

        assertUnavailable(LmsFacultySubmissionQueryService.SUBMISSION_UNAVAILABLE, () -> service.submissionDetail(FACULTY_A, 5L, 14L));
        assertUnavailable(LmsFacultySubmissionQueryService.SUBMISSION_UNAVAILABLE, () -> service.submissionDetail(FACULTY_A, 5L, 999L));
    }

    private LmsSubmissionAttachment storedFile(LmsAssignment of, long id) {
        LmsSubmissionAttempt attempt = attempt(submission(of, 20 + id, "2408400100099", 1), 500 + id, 1, "2026-10-20T10:00:00Z");
        LmsSubmissionAttachment file = new LmsSubmissionAttachment(attempt, "lms/submissions/key-" + id, "answer-" + id + ".pdf",
                "application/pdf", 9);
        ReflectionTestUtils.setField(file, "id", id);
        when(attachments.findWithSubmissionById(id)).thenReturn(Optional.of(file));
        return file;
    }

    @Test
    void facultyWhoMayReviewTheAssignmentDownloadsTheFileFromPrivateStorage() {
        storedFile(assignment, 31L);
        when(storage.open("lms/submissions/key-31")).thenReturn(new ByteArrayInputStream(new byte[9]));

        assertEquals("answer-31.pdf", service.openAttachment(FACULTY_A, 31L).fileName());
    }

    @Test
    void otherFacultyAndFilesOfAssignmentsNotUnderReviewLookLikeMissingFiles() {
        storedFile(assignment, 31L);
        storedFile(SubmissionFixtures.assignment(course, 9, LmsContentStatus.ARCHIVED), 32L);

        for (Executable action : List.<Executable>of(
                () -> service.openAttachment(FACULTY_B, 31L),
                () -> service.openAttachment(FACULTY_A, 32L),
                () -> service.openAttachment(FACULTY_A, 999L),
                () -> service.openAttachment(FACULTY_A, null))) {
            assertUnavailable(LmsFacultySubmissionQueryService.ATTACHMENT_UNAVAILABLE, action);
        }
        verify(storage, never()).open(anyString());
    }

    @Test
    void unreadableStorageIsReportedAsUnavailableStorage() {
        storedFile(assignment, 31L);
        when(storage.open(anyString())).thenThrow(new ObjectStorageException("down", null));

        ApiException refused = assertThrows(ApiException.class, () -> service.openAttachment(FACULTY_A, 31L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, refused.getStatus());
    }
}
