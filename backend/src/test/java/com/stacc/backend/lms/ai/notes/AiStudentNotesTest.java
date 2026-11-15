package com.stacc.backend.lms.ai.notes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.ai.AiCourseKeys;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.AiServiceException;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** Private notes belong to the student in the token, stay within the quota, and are deleted for real, index included. */
class AiStudentNotesTest {

    private static final long STUDENT = 11L;
    private static final long OTHER = 12L;
    private static final long COURSE = 7L;

    private final AiStudentNoteRepository repository = mock(AiStudentNoteRepository.class);
    private final AiStudentNoteIndexRepository indexes = mock(AiStudentNoteIndexRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final LmsStudentAccessService access = mock(LmsStudentAccessService.class);
    private final AiServiceClient client = mock(AiServiceClient.class);
    private final AiStudentNoteService service = new AiStudentNoteService(repository, accounts, access);
    private final AiStudentNoteIndexer indexer = new AiStudentNoteIndexer(service, indexes, client,
            Clock.fixed(Instant.parse("2026-11-15T05:00:00Z"), ZoneOffset.UTC));
    private final List<AiStudentNote> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(access.canAccessCourse(STUDENT, COURSE)).thenReturn(true);
        when(accounts.findByIdForUpdate(STUDENT)).thenReturn(Optional.of(mock(UserAccount.class)));
        when(repository.save(any())).thenAnswer(invocation -> {
            AiStudentNote note = invocation.getArgument(0);
            ReflectionTestUtils.setField(note, "id", 4L + stored.size());
            stored.add(note);
            return note;
        });
        when(repository.findByLmsCourseIdAndUserAccountIdOrderByIdAsc(COURSE, STUDENT)).thenAnswer(invocation -> List.copyOf(stored));
        when(client.isEnabled()).thenReturn(true);
        when(client.replaceIndex(anyString(), any())).thenAnswer(invocation -> {
            ReplaceIndexRequest request = invocation.getArgument(1);
            return new ReplaceIndexResult(request.sources().size(), 0, 1, request.contentFingerprint());
        });
    }

    @Test
    void notesAreKeptWithinTheQuotaAndOnlyForTheOwner() {
        AiStudentNote note = service.create(STUDENT, COURSE, "Summary", "Normal forms.");
        assertThat(note.getUserAccountId()).isEqualTo(STUDENT);

        when(repository.countByLmsCourseIdAndUserAccountId(COURSE, STUDENT)).thenReturn(20L);
        assertRefused(HttpStatus.CONFLICT, () -> service.create(STUDENT, COURSE, "One more", "Text"));
        assertRefused(HttpStatus.NOT_FOUND, () -> service.list(OTHER, COURSE));
        // Another student's note, or one in another course, is never found for this student.
        assertRefused(HttpStatus.NOT_FOUND, () -> service.update(STUDENT, COURSE, 99L, "Mine", "Text"));
        assertRefused(HttpStatus.NOT_FOUND, () -> service.delete(STUDENT, COURSE, 99L));
    }

    @Test
    void theNotesIndexIsBuiltOnlyWhenNotesChanged() {
        assertThat(indexer.current(COURSE, STUDENT)).isEmpty();
        verify(client, never()).replaceIndex(anyString(), any());

        service.create(STUDENT, COURSE, "Summary", "Normal forms.");
        AiStudentNoteIndexer.Scope scope = indexer.current(COURSE, STUDENT).orElseThrow();
        assertThat(scope.index().indexKey()).isEqualTo(AiCourseKeys.forStudentNotes(COURSE, STUDENT));
        ArgumentCaptor<ReplaceIndexRequest> sent = ArgumentCaptor.forClass(ReplaceIndexRequest.class);
        verify(client).replaceIndex(eq(AiCourseKeys.forStudentNotes(COURSE, STUDENT)), sent.capture());
        assertThat(sent.getValue().sources()).extracting(source -> source.sourceType() + ":" + source.title())
                .containsExactly("NOTE:Summary");
        assertThat(sent.getValue().toString()).doesNotContain(String.valueOf(STUDENT) + ":");

        when(indexes.findByLmsCourseIdAndUserAccountId(COURSE, STUDENT)).thenReturn(Optional.of(
                new AiStudentNoteIndex(COURSE, STUDENT, scope.index().indexFingerprint(), Instant.EPOCH)));
        indexer.current(COURSE, STUDENT);
        verify(client).replaceIndex(anyString(), any());
    }

    @Test
    void deletingTheLastNoteRemovesTheIndexFiles() {
        AiStudentNote note = service.create(STUDENT, COURSE, "Summary", "Normal forms.");
        when(repository.findByIdAndLmsCourseIdAndUserAccountId(4L, COURSE, STUDENT)).thenReturn(Optional.of(note));
        org.mockito.Mockito.doAnswer(invocation -> stored.remove(invocation.getArgument(0))).when(repository).delete(any());

        service.delete(STUDENT, COURSE, 4L);
        indexer.afterDelete(COURSE, STUDENT);

        verify(repository).delete(note);
        verify(client).deleteIndex(AiCourseKeys.forStudentNotes(COURSE, STUDENT));
        verify(indexes).deleteForStudent(COURSE, STUDENT);
    }

    @Test
    void anUnreachableServiceDoesNotUndoTheDeletion() {
        org.mockito.Mockito.doThrow(new AiServiceException(AiServiceException.Reason.UNAVAILABLE))
                .when(client).deleteIndex(anyString());
        indexer.afterDelete(COURSE, STUDENT);
        verify(indexes, never()).deleteForStudent(any(), any());
    }

    private static void assertRefused(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(status));
    }
}
