package com.stacc.backend.lms.ai.saved;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.notes.AiStudentNoteKnowledge;
import com.stacc.backend.lms.ai.notes.AiStudentNoteService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved answers (v0.15.1, decision 6): a student saves only the AI answers they choose, at most 50 per course, in a
 * course they may use now. The student comes from the access token; the sources are rebuilt by the server from the
 * course's published content and the student's own notes, so nothing a page sends can name another source. Deleting
 * removes an answer for good.
 */
@Service
@Transactional
public class AiSavedAnswerService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String ANSWER_UNAVAILABLE = "Saved answer not found.";
    static final int MAX_SAVED = 50;
    static final String TOO_MANY = "You can keep at most 50 saved answers in a course. Delete one first.";

    /** A source the page says the answer cited: only its kind, ID, and place; the title comes from the server. */
    public record SourceRef(String sourceType, long sourceId, Integer pageNumber, Integer slideNumber) {
    }

    private final AiSavedAnswerRepository answers;
    private final UserAccountRepository accounts;
    private final LmsStudentAccessService studentAccess;
    private final AiKnowledgeSources sources;
    private final AiStudentNoteService notes;

    public AiSavedAnswerService(AiSavedAnswerRepository answers, UserAccountRepository accounts, LmsStudentAccessService studentAccess,
            AiKnowledgeSources sources, AiStudentNoteService notes) {
        this.answers = answers;
        this.accounts = accounts;
        this.studentAccess = studentAccess;
        this.sources = sources;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public List<AiSavedAnswer> list(Long accountId, Long lmsCourseId) {
        requireCourse(accountId, lmsCourseId);
        return answers.findByLmsCourseIdAndUserAccountIdOrderByIdDesc(lmsCourseId, accountId);
    }

    /** Saves within the quota: the account row is locked first and the count runs at READ COMMITTED. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AiSavedAnswer save(Long accountId, Long lmsCourseId, String question, String answer, List<SourceRef> cited) {
        requireCourse(accountId, lmsCourseId);
        accounts.findByIdForUpdate(accountId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        if (answers.countByLmsCourseIdAndUserAccountId(lmsCourseId, accountId) >= MAX_SAVED) {
            throw new ApiException(HttpStatus.CONFLICT, TOO_MANY);
        }
        List<AiSavedSource> rebuilt = rebuild(cited == null ? List.of() : cited, sources.publishedKnowledge(lmsCourseId),
                notes.knowledge(lmsCourseId, accountId));
        try {
            return answers.save(new AiSavedAnswer(lmsCourseId, accountId, question, answer, rebuilt));
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the question and the answer.");
        }
    }

    /** Deletes a saved answer of the student for good. */
    public void delete(Long accountId, Long lmsCourseId, Long savedAnswerId) {
        requireCourse(accountId, lmsCourseId);
        answers.delete(answers.findByIdAndLmsCourseIdAndUserAccountId(savedAnswerId, lmsCourseId, accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ANSWER_UNAVAILABLE)));
    }

    /** Only sources the student can use now keep: published topics and files of the course, and the student's own notes. */
    static List<AiSavedSource> rebuild(List<SourceRef> cited, AiCourseKnowledge knowledge, AiStudentNoteKnowledge myNotes) {
        Set<String> seen = new LinkedHashSet<>();
        List<AiSavedSource> rebuilt = new ArrayList<>();
        for (SourceRef ref : cited) {
            if (ref == null || ref.sourceType() == null || rebuilt.size() >= 12
                    || !seen.add(ref.sourceType() + ":" + ref.sourceId() + ":" + ref.pageNumber() + ":" + ref.slideNumber())) {
                continue;
            }
            int position = rebuilt.size() + 1;
            switch (ref.sourceType()) {
                case "TOPIC" -> knowledge.topic(ref.sourceId()).ifPresent(topic -> rebuilt.add(new AiSavedSource(position, "TOPIC",
                        topic.id(), topic.title(), null, null, null, topic.id())));
                case "STUDY_MATERIAL" -> knowledge.material(ref.sourceId()).ifPresent(material -> rebuilt.add(new AiSavedSource(position,
                        "STUDY_MATERIAL", material.id(), material.title(), material.fileName(), positive(ref.pageNumber()),
                        positive(ref.slideNumber()), material.topicId())));
                case "NOTE" -> myNotes.note(ref.sourceId()).ifPresent(note -> rebuilt.add(new AiSavedSource(position, "NOTE",
                        note.id(), note.title(), null, null, null, null)));
                default -> {
                    // Teacher documents and anything else are never a student's source.
                }
            }
        }
        return rebuilt;
    }

    private static Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private void requireCourse(Long accountId, Long lmsCourseId) {
        if (lmsCourseId == null || !studentAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }
}
