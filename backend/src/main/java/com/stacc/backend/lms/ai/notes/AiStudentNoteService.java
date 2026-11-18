package com.stacc.backend.lms.ai.notes;

import java.util.List;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AiStudentNoteService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String NOTE_UNAVAILABLE = "Note not found.";
    static final int MAX_NOTES = 20;
    static final String TOO_MANY = "You can keep at most 20 notes in a course. Delete one first.";

    private final AiStudentNoteRepository notes;
    private final UserAccountRepository accounts;
    private final LmsStudentAccessService studentAccess;

    public AiStudentNoteService(AiStudentNoteRepository notes, UserAccountRepository accounts,
            LmsStudentAccessService studentAccess) {
        this.notes = notes;
        this.accounts = accounts;
        this.studentAccess = studentAccess;
    }

    @Transactional(readOnly = true)
    public List<AiStudentNote> list(Long accountId, Long lmsCourseId) {
        requireCourse(accountId, lmsCourseId);
        return notes.findByLmsCourseIdAndUserAccountIdOrderByIdAsc(lmsCourseId, accountId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AiStudentNote create(Long accountId, Long lmsCourseId, String title, String content) {
        requireCourse(accountId, lmsCourseId);
        accounts.findByIdForUpdate(accountId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        if (notes.countByLmsCourseIdAndUserAccountId(lmsCourseId, accountId) >= MAX_NOTES) {
            throw new ApiException(HttpStatus.CONFLICT, TOO_MANY);
        }
        return notes.save(new AiStudentNote(lmsCourseId, accountId, title, content));
    }

    public AiStudentNote update(Long accountId, Long lmsCourseId, Long noteId, String title, String content) {
        AiStudentNote note = owned(accountId, lmsCourseId, noteId);
        note.edit(title, content);
        return note;
    }

    public void delete(Long accountId, Long lmsCourseId, Long noteId) {
        notes.delete(owned(accountId, lmsCourseId, noteId));
    }

    @Transactional(readOnly = true)
    public AiStudentNoteKnowledge knowledge(Long lmsCourseId, Long accountId) {
        return AiStudentNoteKnowledge.of(notes.findByLmsCourseIdAndUserAccountIdOrderByIdAsc(lmsCourseId, accountId).stream()
                .map(note -> new AiStudentNoteKnowledge.Note(note.getId(), note.getTitle(), note.getContent()))
                .toList());
    }

    private void requireCourse(Long accountId, Long lmsCourseId) {
        if (lmsCourseId == null || !studentAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }

    private AiStudentNote owned(Long accountId, Long lmsCourseId, Long noteId) {
        requireCourse(accountId, lmsCourseId);
        return notes.findByIdAndLmsCourseIdAndUserAccountId(noteId, lmsCourseId, accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOTE_UNAVAILABLE));
    }
}
