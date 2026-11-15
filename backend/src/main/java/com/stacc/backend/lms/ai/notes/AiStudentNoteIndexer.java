package com.stacc.backend.lms.ai.notes;

import java.time.Clock;
import java.util.Optional;

import com.stacc.backend.lms.ai.AiCourseKeys;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.AiServiceException;
import com.stacc.backend.lms.ai.AiServiceMessages.IndexRef;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Keeps one student's notes index in one course current (v0.13.6). It runs outside any transaction, so no row lock is
 * held while the AI service works. The index is rebuilt only when the notes changed since it was last built, and it is
 * removed with all its files once the student has no note left. Callers check the student's course access first.
 */
@Service
public class AiStudentNoteIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiStudentNoteIndexer.class);

    /** The notes index to search with the course's own, and the notes it may cite. */
    public record Scope(IndexRef index, AiStudentNoteKnowledge knowledge) {
    }

    private final AiStudentNoteService notes;
    private final AiStudentNoteIndexRepository indexes;
    private final AiServiceClient client;
    private final Clock clock;

    public AiStudentNoteIndexer(AiStudentNoteService notes, AiStudentNoteIndexRepository indexes, AiServiceClient client,
            Clock clock) {
        this.notes = notes;
        this.indexes = indexes;
        this.client = client;
        this.clock = clock;
    }

    /**
     * The student's current notes index, rebuilt first if the notes changed; empty when they have no notes. Throws
     * {@link AiServiceException} when the AI service cannot build it.
     */
    public Optional<Scope> current(Long lmsCourseId, Long accountId) {
        AiStudentNoteKnowledge knowledge = notes.knowledge(lmsCourseId, accountId);
        String key = AiCourseKeys.forStudentNotes(lmsCourseId, accountId);
        Optional<AiStudentNoteIndex> row = indexes.findByLmsCourseIdAndUserAccountId(lmsCourseId, accountId);
        if (knowledge.isEmpty()) {
            row.ifPresent(stale -> remove(lmsCourseId, accountId, key));
            return Optional.empty();
        }
        if (row.isEmpty() || !knowledge.fingerprint().equals(row.get().getContentFingerprint())) {
            ReplaceIndexResult result = client.replaceIndex(key,
                    new ReplaceIndexRequest(knowledge.fingerprint(), knowledge.indexSources()));
            if (result.indexedSourceCount() == null || !knowledge.fingerprint().equals(result.contentFingerprint())) {
                throw new AiServiceException(AiServiceException.Reason.BAD_RESPONSE);
            }
            record(lmsCourseId, accountId, knowledge.fingerprint());
        }
        return Optional.of(new Scope(new IndexRef(key, knowledge.fingerprint(), knowledge.allowedSources()), knowledge));
    }

    /**
     * After a note is deleted: the index is rebuilt without it, or removed with its files when no note is left. If the
     * AI service cannot be reached now, the deleted note is still never used (it is no longer an allowed source), and
     * the index is rebuilt or removed the next time the student uses their notes.
     */
    public void afterDelete(Long lmsCourseId, Long accountId) {
        if (!client.isEnabled()) {
            return;
        }
        try {
            if (notes.knowledge(lmsCourseId, accountId).isEmpty()) {
                remove(lmsCourseId, accountId, AiCourseKeys.forStudentNotes(lmsCourseId, accountId));
            } else {
                current(lmsCourseId, accountId);
            }
        } catch (AiServiceException exception) {
            LOGGER.warn("A notes index could not be updated after a deletion: {}", exception.reason());
        }
    }

    private void remove(Long lmsCourseId, Long accountId, String key) {
        client.deleteIndex(key);
        indexes.deleteForStudent(lmsCourseId, accountId);
    }

    private void record(Long lmsCourseId, Long accountId, String fingerprint) {
        AiStudentNoteIndex row = indexes.findByLmsCourseIdAndUserAccountId(lmsCourseId, accountId)
                .orElseGet(() -> new AiStudentNoteIndex(lmsCourseId, accountId, fingerprint, clock.instant()));
        row.indexed(fingerprint, clock.instant());
        try {
            indexes.saveAndFlush(row);
        } catch (DataIntegrityViolationException twice) {
            // Two requests built the same notes at once; the other one recorded it.
        }
    }
}
