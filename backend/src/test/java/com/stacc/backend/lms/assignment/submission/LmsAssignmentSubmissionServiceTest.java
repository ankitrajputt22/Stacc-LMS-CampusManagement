package com.stacc.backend.lms.assignment.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsMaterialProperties;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.membership.LmsStudentMembership;
import com.stacc.backend.lms.membership.LmsStudentMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Student A (account 12, course enrollment 40) may use LMS course 7, which has published assignment 5.
 * The access service, the repositories, and the storage are stand-ins: the access rules themselves are
 * tested with LmsStudentAccessService. The clock is fixed, and the deadline is 2026-10-20T18:00Z.
 */
class LmsAssignmentSubmissionServiceTest {

    private static final Long STUDENT_A = 12L;
    private static final Long STUDENT_B = 13L;
    private static final Instant BEFORE_DUE = Instant.parse("2026-10-20T17:00:00Z");
    private static final Instant AFTER_DUE = Instant.parse("2026-10-20T19:00:00.123456789Z");
    private static final byte[] PDF = "%PDF-1.4 made-up test file".getBytes(StandardCharsets.US_ASCII);

    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final LmsAssignmentRepository assignmentRows = mock(LmsAssignmentRepository.class);
    private final LmsStudentMembershipRepository memberships = mock(LmsStudentMembershipRepository.class);
    private final LmsAssignmentSubmissionRepository submissions = mock(LmsAssignmentSubmissionRepository.class);
    private final LmsSubmissionAttemptRepository attempts = mock(LmsSubmissionAttemptRepository.class);
    private final LmsSubmissionAttachmentRepository attachments = mock(LmsSubmissionAttachmentRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);

    private final LmsCourse course = SubmissionFixtures.course(7);
    private final LmsCourse otherCourse = SubmissionFixtures.course(8);
    private final LmsStudentMembership membershipA = SubmissionFixtures.membership(course, STUDENT_A, 40);
    private final LmsStudentMembership membershipB = SubmissionFixtures.membership(course, STUDENT_B, 41);
    private final LmsAssignment published = SubmissionFixtures.assignment(course, 5, LmsContentStatus.PUBLISHED);

    private final AtomicLong nextId = new AtomicLong(100);
    private final List<LmsAssignmentSubmission> storedSubmissions = new ArrayList<>();

    private LmsAssignmentSubmissionService serviceAt(Instant now) {
        return new LmsAssignmentSubmissionService(studentAccess, assignmentRows, memberships, submissions, attempts, attachments,
                storage, Clock.fixed(now, ZoneOffset.UTC), new LmsMaterialProperties(DataSize.ofBytes(1024)),
                new LmsSubmissionProperties(3));
    }

    private final LmsAssignmentSubmissionService service = serviceAt(BEFORE_DUE);

    @BeforeEach
    void studentAWhoMayUseCourseSeven() {
        when(studentAccess.findAccessGrantingMembership(STUDENT_A, 7L)).thenReturn(Optional.of(membershipA));
        when(studentAccess.findAccessGrantingMembership(STUDENT_B, 7L)).thenReturn(Optional.of(membershipB));
        when(assignmentRows.findById(5L)).thenReturn(Optional.of(published));
        when(memberships.findByIdForUpdate(anyLong())).thenAnswer(call ->
                Optional.of(call.getArgument(0).equals(membershipA.getId()) ? membershipA : membershipB));
        // The database stand-in: one submission row per assignment and enrollment, IDs given on save.
        when(submissions.findForUpdate(anyLong(), anyLong())).thenAnswer(call -> storedSubmissions.stream()
                .filter(row -> row.getAssignment().getId().equals(call.getArgument(0))
                        && row.getCourseEnrollment().getId().equals(call.getArgument(1)))
                .findFirst());
        when(submissions.saveAndFlush(any(LmsAssignmentSubmission.class))).thenAnswer(call -> {
            LmsAssignmentSubmission row = call.getArgument(0);
            if (row.getId() == null) {
                ReflectionTestUtils.setField(row, "id", nextId.incrementAndGet());
                storedSubmissions.add(row);
            }
            return row;
        });
        when(attempts.saveAndFlush(any(LmsSubmissionAttempt.class))).thenAnswer(call -> withId(call.getArgument(0)));
        when(attachments.saveAllAndFlush(anyList())).thenAnswer(call -> {
            List<LmsSubmissionAttachment> rows = call.getArgument(0);
            rows.forEach(this::withId);
            return rows;
        });
    }

    private <T> T withId(T row) {
        ReflectionTestUtils.setField(row, "id", nextId.incrementAndGet());
        return row;
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", PDF);
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    private void assertNothingStoredOrSaved() {
        verifyNoInteractions(storage);
        verify(attempts, never()).saveAndFlush(any());
        verify(submissions, never()).saveAndFlush(any());
    }

    // --- eligibility ---------------------------------------------------------------------------------

    @Test
    void publishedAssignmentInACourseTheStudentMayUseAcceptsAnAttempt() {
        LmsAssignmentSubmissionService.SubmittedAttempt submitted = service.submit(STUDENT_A, 7L, 5L, "My answer", null);

        LmsSubmissionAttempt attempt = submitted.attempt();
        assertEquals(1, attempt.getAttemptNumber());
        assertEquals("My answer", attempt.getTextContent());
        assertEquals(BEFORE_DUE, attempt.getSubmittedAt());
        assertSame(published, attempt.getSubmission().getAssignment());
        assertSame(membershipA.getCourseEnrollment(), attempt.getSubmission().getCourseEnrollment());
        assertEquals(List.of(), submitted.attachments());
        verifyNoInteractions(storage);
    }

    @Test
    void draftArchivedForeignAndMissingAssignmentsAndCoursesWithoutAccessAllLookTheSame() {
        when(assignmentRows.findById(6L)).thenReturn(Optional.of(SubmissionFixtures.assignment(course, 6, LmsContentStatus.DRAFT)));
        when(assignmentRows.findById(9L)).thenReturn(Optional.of(SubmissionFixtures.assignment(course, 9, LmsContentStatus.ARCHIVED)));
        when(assignmentRows.findById(8L)).thenReturn(Optional.of(SubmissionFixtures.assignment(otherCourse, 8, LmsContentStatus.PUBLISHED)));

        for (Executable action : List.<Executable>of(
                () -> service.submit(STUDENT_A, 7L, 6L, "Answer", null),
                () -> service.submit(STUDENT_A, 7L, 9L, "Answer", null),
                () -> service.submit(STUDENT_A, 7L, 8L, "Answer", null),
                () -> service.submit(STUDENT_A, 7L, 999L, "Answer", null),
                () -> service.submit(STUDENT_A, 8L, 8L, "Answer", null),
                () -> service.submit(99L, 7L, 5L, "Answer", null),
                () -> service.history(STUDENT_A, 7L, 6L),
                () -> service.history(99L, 7L, 5L))) {
            assertRefused(HttpStatus.NOT_FOUND, LmsAssignmentSubmissionService.ASSIGNMENT_UNAVAILABLE, action);
        }
        assertNothingStoredOrSaved();
    }

    @Test
    void courseEnrollmentComesFromTheAccessRuleForTheSignedInAccountOnly() {
        service.submit(STUDENT_A, 7L, 5L, "From A", null);
        service.submit(STUDENT_B, 7L, 5L, "From B", null);

        assertEquals(2, storedSubmissions.size());
        assertEquals(40L, storedSubmissions.get(0).getCourseEnrollment().getId());
        assertEquals(41L, storedSubmissions.get(1).getCourseEnrollment().getId());
        assertEquals(1, storedSubmissions.get(1).getLatestAttemptNumber());
    }

    // --- content -------------------------------------------------------------------------------------

    @Test
    void textOnlyFileOnlyAndTextWithFilesAreAllValid() {
        LmsAssignmentSubmissionService.SubmittedAttempt fileOnly = service.submit(STUDENT_A, 7L, 5L, null, List.of(pdf("only.pdf")));
        LmsAssignmentSubmissionService.SubmittedAttempt both = service.submit(STUDENT_A, 7L, 5L, "Both", List.of(pdf("a.pdf"), pdf("b.pdf")));
        LmsAssignmentSubmissionService.SubmittedAttempt textOnly = service.submit(STUDENT_A, 7L, 5L, "Text only", List.of());

        assertNull(fileOnly.attempt().getTextContent());
        assertEquals(List.of("only.pdf"), fileOnly.attachments().stream().map(LmsSubmissionAttachment::getOriginalFileName).toList());
        assertEquals(List.of("a.pdf", "b.pdf"), both.attachments().stream().map(LmsSubmissionAttachment::getOriginalFileName).toList());
        assertEquals("Text only", textOnly.attempt().getTextContent());
        assertEquals(List.of(), textOnly.attachments());
    }

    @Test
    void attemptWithoutTextOrFilesIsRefused() {
        MockMultipartFile noFileChosen = new MockMultipartFile("files", "", "application/octet-stream", new byte[0]);

        assertRefused(HttpStatus.BAD_REQUEST, LmsAssignmentSubmissionService.NO_CONTENT, () -> service.submit(STUDENT_A, 7L, 5L, null, null));
        assertRefused(HttpStatus.BAD_REQUEST, LmsAssignmentSubmissionService.NO_CONTENT,
                () -> service.submit(STUDENT_A, 7L, 5L, "  \n ", List.of()));
        assertRefused(HttpStatus.BAD_REQUEST, LmsAssignmentSubmissionService.NO_CONTENT,
                () -> service.submit(STUDENT_A, 7L, 5L, "", List.of(noFileChosen)));
        assertNothingStoredOrSaved();
    }

    @Test
    void moreFilesThanAllowedOrTooMuchTextIsRefused() {
        assertRefused(HttpStatus.BAD_REQUEST, "Attach at most 3 files.", () -> service.submit(STUDENT_A, 7L, 5L, "Answer",
                List.of(pdf("1.pdf"), pdf("2.pdf"), pdf("3.pdf"), pdf("4.pdf"))));
        assertRefused(HttpStatus.BAD_REQUEST, LmsAssignmentSubmissionService.TEXT_TOO_LONG,
                () -> service.submit(STUDENT_A, 7L, 5L, "x".repeat(LmsSubmissionAttempt.MAX_TEXT_LENGTH + 1), null));
        assertNothingStoredOrSaved();
    }

    @Test
    void filesAreCheckedWithTheStudyMaterialRulesAndRefusalsNameTheFile() {
        MockMultipartFile tooLarge = new MockMultipartFile("files", "big.pdf", "application/pdf", new byte[2048]);
        MockMultipartFile program = new MockMultipartFile("files", "tool.exe", "application/octet-stream", PDF);
        MockMultipartFile disguised = new MockMultipartFile("files", "notes.pdf", "application/pdf", "<html>".getBytes(StandardCharsets.UTF_8));

        assertRefused(HttpStatus.BAD_REQUEST, "“big.pdf”: The file is larger than the 1024 byte limit.",
                () -> service.submit(STUDENT_A, 7L, 5L, null, List.of(pdf("fine.pdf"), tooLarge)));
        ApiException type = assertThrows(ApiException.class, () -> service.submit(STUDENT_A, 7L, 5L, null, List.of(program)));
        assertTrue(type.getMessage().startsWith("“tool.exe”: This type of file is not allowed."));
        assertRefused(HttpStatus.BAD_REQUEST, "“notes.pdf”: The file's contents do not match its type.",
                () -> service.submit(STUDENT_A, 7L, 5L, null, List.of(disguised)));
        // Every file is checked before any is stored.
        assertNothingStoredOrSaved();
    }

    // --- attempts --------------------------------------------------------------------------------------

    @Test
    void everyHandInIsANewAttemptAndEarlierAttemptsStayAsTheyWere() {
        LmsSubmissionAttempt first = service.submit(STUDENT_A, 7L, 5L, "First", null).attempt();
        LmsSubmissionAttempt second = service.submit(STUDENT_A, 7L, 5L, "Second", List.of(pdf("second.pdf"))).attempt();
        LmsSubmissionAttempt third = service.submit(STUDENT_A, 7L, 5L, "Third", List.of(pdf("a.pdf"), pdf("b.pdf"))).attempt();

        assertEquals(List.of(1, 2, 3), List.of(first.getAttemptNumber(), second.getAttemptNumber(), third.getAttemptNumber()));
        assertEquals(1, storedSubmissions.size());
        assertEquals(3, storedSubmissions.get(0).getLatestAttemptNumber());
        assertEquals("First", first.getTextContent());
        assertEquals(BEFORE_DUE, first.getSubmittedAt());
        assertNotEquals(first.getId(), third.getId());
        verify(attempts, never()).delete(any());
        verify(attempts, never()).deleteAll();
    }

    @Test
    void filesAreStoredBeforeTheStudentsRecordsAreLockedAndTheNumberIsReservedUnderTheLock() {
        service.submit(STUDENT_A, 7L, 5L, null, List.of(pdf("solution.pdf")));

        InOrder order = inOrder(storage, memberships, submissions, attempts);
        order.verify(storage).store(anyString(), any(InputStream.class), eq((long) PDF.length), eq("application/pdf"));
        order.verify(memberships).findByIdForUpdate(membershipA.getId());
        order.verify(submissions).findForUpdate(5L, 40L);
        order.verify(attempts).saveAndFlush(any(LmsSubmissionAttempt.class));
    }

    @Test
    void storageKeysAreMadeByTheServerAndNeverFromTheFileName() {
        LmsAssignmentSubmissionService.SubmittedAttempt submitted = service.submit(STUDENT_A, 7L, 5L, null,
                List.of(pdf("../../secret name.pdf"), pdf("../../secret name.pdf")));

        List<String> keys = submitted.attachments().stream().map(LmsSubmissionAttachment::getObjectKey).toList();
        assertEquals(2, keys.stream().distinct().count());
        for (String key : keys) {
            assertTrue(key.matches("lms/submissions/assignments/5/enrollments/40/[0-9a-f-]{36}"), key);
            assertFalse(key.contains("secret") || key.contains("TEST-STUDENT"), key);
        }
        assertEquals("secret name.pdf", submitted.attachments().get(0).getOriginalFileName());
        assertEquals("application/pdf", submitted.attachments().get(0).getContentType());
        verify(storage).store(eq(keys.get(0)), any(InputStream.class), eq((long) PDF.length), eq("application/pdf"));
        verify(storage).store(eq(keys.get(1)), any(InputStream.class), eq((long) PDF.length), eq("application/pdf"));
    }

    @Test
    void storageFailureSavesNothingAndLeavesNoStoredFileBehind() {
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        doThrow(new ObjectStorageException("down", null))
                .when(storage).store(stored.capture(), any(InputStream.class), anyLong(), anyString());

        assertRefused(HttpStatus.SERVICE_UNAVAILABLE, LmsAssignmentSubmissionService.STORAGE_UNAVAILABLE,
                () -> service.submit(STUDENT_A, 7L, 5L, "Answer", List.of(pdf("a.pdf"))));

        verify(storage).delete(stored.getValue());
        verify(memberships, never()).findByIdForUpdate(anyLong());
        verify(attempts, never()).saveAndFlush(any());
    }

    @Test
    void failureOnALaterFileRemovesTheEarlierOnesOfTheSameAttempt() {
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        doNothing()
                .doThrow(new ObjectStorageException("down", null))
                .when(storage).store(stored.capture(), any(InputStream.class), anyLong(), anyString());

        assertRefused(HttpStatus.SERVICE_UNAVAILABLE, LmsAssignmentSubmissionService.STORAGE_UNAVAILABLE,
                () -> service.submit(STUDENT_A, 7L, 5L, "Answer", List.of(pdf("a.pdf"), pdf("b.pdf"))));

        verify(storage).delete(stored.getAllValues().get(0));
        verify(storage).delete(stored.getAllValues().get(1));
        verify(attempts, never()).saveAndFlush(any());
    }

    @Test
    void failureToSaveAfterStoringRemovesTheStoredFiles() {
        when(attachments.saveAllAndFlush(anyList())).thenThrow(new IllegalStateException("database unavailable"));
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);

        assertThrows(IllegalStateException.class,
                () -> service.submit(STUDENT_A, 7L, 5L, "Answer", List.of(pdf("a.pdf"), pdf("b.pdf"))));

        verify(storage, times(2)).store(stored.capture(), any(InputStream.class), anyLong(), anyString());
        verify(storage).delete(stored.getAllValues().get(0));
        verify(storage).delete(stored.getAllValues().get(1));
    }

    // --- deadline ----------------------------------------------------------------------------------

    @Test
    void lateWorkIsAcceptedAndMarkedAgainstTheDeadlineThatAppliedThen() {
        LmsSubmissionAttempt onTime = service.submit(STUDENT_A, 7L, 5L, "Early", null).attempt();
        LmsSubmissionAttempt late = serviceAt(AFTER_DUE).submit(STUDENT_A, 7L, 5L, "Late", null).attempt();

        assertFalse(onTime.isLate());
        assertTrue(late.isLate());
        assertEquals(SubmissionFixtures.DUE, late.getDueAtSnapshot());
        // Stored at the precision the database keeps.
        assertEquals(Instant.parse("2026-10-20T19:00:00.123456Z"), late.getSubmittedAt());

        published.edit(published.getTitle(), published.getInstructions(), Instant.parse("2026-10-22T18:00:00Z"), published.getMaxMarks());
        LmsSubmissionAttempt afterExtension = serviceAt(AFTER_DUE).submit(STUDENT_A, 7L, 5L, "After the extension", null).attempt();

        assertTrue(late.isLate());
        assertEquals(SubmissionFixtures.DUE, late.getDueAtSnapshot());
        assertFalse(afterExtension.isLate());
    }

    // --- history ---------------------------------------------------------------------------------------

    @Test
    void studentWhoHasNotHandedInGetsAnEmptyHistory() {
        when(submissions.findByAssignmentIdAndCourseEnrollmentId(5L, 40L)).thenReturn(Optional.empty());

        assertEquals(List.of(), service.history(STUDENT_A, 7L, 5L));
    }

    @Test
    void historyIsTheStudentsOwnAttemptsNewestFirstWithTheirFiles() {
        LmsAssignmentSubmission own = new LmsAssignmentSubmission(published, membershipA.getCourseEnrollment());
        ReflectionTestUtils.setField(own, "id", 70L);
        LmsSubmissionAttempt first = withId(new LmsSubmissionAttempt(own, 1, "First", BEFORE_DUE, SubmissionFixtures.DUE));
        LmsSubmissionAttempt second = withId(new LmsSubmissionAttempt(own, 2, null, AFTER_DUE, SubmissionFixtures.DUE));
        LmsSubmissionAttachment file = withId(new LmsSubmissionAttachment(second, "lms/submissions/k", "a.pdf", "application/pdf", 5));
        when(submissions.findByAssignmentIdAndCourseEnrollmentId(5L, 40L)).thenReturn(Optional.of(own));
        when(attempts.findAllBySubmissionIdOrderByAttemptNumberDesc(70L)).thenReturn(List.of(second, first));
        when(attachments.findAllByAttemptIdInOrderByIdAsc(List.of(second.getId(), first.getId()))).thenReturn(List.of(file));

        List<LmsAssignmentSubmissionService.SubmittedAttempt> history = service.history(STUDENT_A, 7L, 5L);

        assertEquals(List.of(2, 1), history.stream().map(entry -> entry.attempt().getAttemptNumber()).toList());
        assertEquals(List.of(file), history.get(0).attachments());
        assertEquals(List.of(), history.get(1).attachments());
        assertTrue(history.get(0).attempt().isLate());
        verify(submissions).findByAssignmentIdAndCourseEnrollmentId(5L, 40L);
    }

    // --- downloads -------------------------------------------------------------------------------------

    private LmsSubmissionAttachment attachmentOf(LmsStudentMembership owner, LmsAssignment assignment, long id) {
        LmsAssignmentSubmission submission = new LmsAssignmentSubmission(assignment, owner.getCourseEnrollment());
        LmsSubmissionAttempt attempt = withId(new LmsSubmissionAttempt(submission, 1, null, BEFORE_DUE, SubmissionFixtures.DUE));
        LmsSubmissionAttachment attachment = new LmsSubmissionAttachment(
                attempt, "lms/submissions/key-" + id, "answer-" + id + ".pdf", "application/pdf", PDF.length);
        ReflectionTestUtils.setField(attachment, "id", id);
        when(attachments.findWithSubmissionById(id)).thenReturn(Optional.of(attachment));
        return attachment;
    }

    @Test
    void studentDownloadsTheirOwnFileFromPrivateStorage() {
        attachmentOf(membershipA, published, 31L);
        when(storage.open("lms/submissions/key-31")).thenReturn(new ByteArrayInputStream(PDF));

        LmsAssignmentSubmissionService.Download download = service.openOwnAttachment(STUDENT_A, 31L);

        assertEquals("answer-31.pdf", download.fileName());
        assertEquals("application/pdf", download.contentType());
        assertEquals(PDF.length, download.sizeBytes());
    }

    @Test
    void anotherStudentsFileAndFilesNoLongerVisibleLookLikeMissingOnes() {
        attachmentOf(membershipA, published, 31L);
        attachmentOf(membershipA, SubmissionFixtures.assignment(course, 9, LmsContentStatus.ARCHIVED), 32L);

        for (Executable action : List.<Executable>of(
                () -> service.openOwnAttachment(STUDENT_B, 31L),
                () -> service.openOwnAttachment(99L, 31L),
                () -> service.openOwnAttachment(STUDENT_A, 32L),
                () -> service.openOwnAttachment(STUDENT_A, 999L),
                () -> service.openOwnAttachment(STUDENT_A, null))) {
            assertRefused(HttpStatus.NOT_FOUND, LmsAssignmentSubmissionService.ATTACHMENT_UNAVAILABLE, action);
        }
        verify(storage, never()).open(anyString());
    }

    @Test
    void ownFileThatStorageCannotReadIsReportedAsUnavailableStorage() {
        attachmentOf(membershipA, published, 31L);
        when(storage.open(anyString())).thenThrow(new ObjectStorageException("down", null));

        assertRefused(HttpStatus.SERVICE_UNAVAILABLE, LmsAssignmentSubmissionService.STORAGE_UNAVAILABLE,
                () -> service.openOwnAttachment(STUDENT_A, 31L));
    }

    @Test
    void submittedFilesAreOptionalPartsOfMultipartRequests() {
        // A list of parts may contain nulls when a client sends odd form data; they are ignored.
        List<MultipartFile> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(pdf("real.pdf"));

        assertEquals(1, service.submit(STUDENT_A, 7L, 5L, null, withNull).attachments().size());
    }
}
