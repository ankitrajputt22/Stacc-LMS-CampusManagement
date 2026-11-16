package com.stacc.backend.lms.ai.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.EngagementProperties;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.ai.AiCourseKeys;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiErrors;
import com.stacc.backend.lms.ai.AiKnowledgeRecords;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.AiServiceException;
import com.stacc.backend.lms.ai.AiServiceMessages;
import com.stacc.backend.lms.ai.AiServiceMessages.FlashcardResult;
import com.stacc.backend.lms.ai.control.AiCourseControlService;
import com.stacc.backend.lms.ai.control.AiQuizPause;
import com.stacc.backend.lms.ai.control.AiRequestGate;
import com.stacc.backend.lms.ai.control.AiRequestLogEntry;
import com.stacc.backend.lms.ai.flashcards.AiFlashcard;
import com.stacc.backend.lms.ai.flashcards.AiFlashcardSet;
import com.stacc.backend.lms.ai.flashcards.AiFlashcardStore;
import com.stacc.backend.lms.ai.flashcards.ReviewRating;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Flashcards (v0.15.3): a student asks AI for a set from a published page, module, file, or the whole course; every card
 * that cites a real source is kept with that source, owned by the student. Reviews follow {@link
 * com.stacc.backend.lms.ai.flashcards.SpacedRepetition}, with "today" in the college's time zone. Access is the student
 * access policy on every request; reviewing and deleting need no AI.
 */
@Service
public class AiFlashcardService {

    static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";

    private final LmsStudentAccessService studentAccess;
    private final AiCourseControlService controls;
    private final AiKnowledgeSources sources;
    private final AiKnowledgeRecords records;
    private final AiServiceClient client;
    private final AiRequestGate gate;
    private final AiFlashcardStore store;
    private final AiQuizPause quizPause;
    private final EngagementProperties college;
    private final Clock clock;

    public AiFlashcardService(LmsStudentAccessService studentAccess, AiCourseControlService controls, AiKnowledgeSources sources,
            AiKnowledgeRecords records, AiServiceClient client, AiRequestGate gate, AiFlashcardStore store,
            EngagementProperties college, Clock clock, AiQuizPause quizPause) {
        this.quizPause = quizPause;
        this.studentAccess = studentAccess;
        this.controls = controls;
        this.sources = sources;
        this.records = records;
        this.client = client;
        this.gate = gate;
        this.store = store;
        this.college = college;
        this.clock = clock;
    }

    public List<AiFlashcardStore.SetSummary> list(Long accountId, Long lmsCourseId) {
        requireCourse(accountId, lmsCourseId);
        return store.list(accountId, lmsCourseId, today());
    }

    public AiFlashcardSet get(Long accountId, Long lmsCourseId, Long setId) {
        requireCourse(accountId, lmsCourseId);
        return store.get(accountId, lmsCourseId, setId);
    }

    public AiFlashcard review(Long accountId, Long lmsCourseId, Long setId, Long cardId, ReviewRating rating) {
        requireCourse(accountId, lmsCourseId);
        return store.review(accountId, lmsCourseId, setId, cardId, rating, today(), clock.instant());
    }

    public void delete(Long accountId, Long lmsCourseId, Long setId) {
        requireCourse(accountId, lmsCourseId);
        store.delete(accountId, lmsCourseId, setId);
    }

    /** Asks AI for cards and saves the cited ones as a new set; nothing is saved when none are usable. */
    public AiFlashcardSet create(Long accountId, Long lmsCourseId, AiFlashcardSetRequest request) {
        requireCourse(accountId, lmsCourseId);
        return gate.run(AiRequestLogEntry.Kind.FLASHCARDS, accountId, lmsCourseId, () -> {
            if (!client.isEnabled()) {
                throw AiErrors.unavailable();
            }
            controls.requireStudentAi(lmsCourseId);
            quizPause.requireNoQuizInProgress(accountId, lmsCourseId);
            AiCourseKnowledge knowledge = sources.publishedKnowledge(lmsCourseId);
            if (!records.find(lmsCourseId).map(index -> index.answersFor(knowledge.fingerprint())).orElse(false)) {
                throw new ApiException(HttpStatus.CONFLICT, AiErrors.STUDY_NOT_READY);
            }
            Scope scope = scope(knowledge, request);
            FlashcardResult result;
            try {
                result = client.flashcards(AiCourseKeys.forLmsCourse(lmsCourseId), new AiServiceMessages.FlashcardRequest(
                        knowledge.fingerprint(), request.cardCount(), scope.topicId(), scope.allowed()));
            } catch (AiServiceException exception) {
                throw AiErrors.of(exception, AiErrors.STUDY_NOT_READY);
            }
            List<AiFlashcardStore.CardDraft> drafts = new ArrayList<>();
            for (AiServiceMessages.Flashcard card : result.cards() == null ? List.<AiServiceMessages.Flashcard>of() : result.cards()) {
                String front = card == null ? null : AiResults.text(card.front(), 300);
                String back = card == null ? null : AiResults.text(card.back(), 1000);
                List<AiCitationResponse> cited = card == null ? List.of() : AiResults.citations(card.citations(), knowledge);
                if (front != null && back != null && !cited.isEmpty() && drafts.size() < request.cardCount()) {
                    AiCitationResponse source = cited.get(0);
                    drafts.add(new AiFlashcardStore.CardDraft(front, back, source.sourceType(), source.sourceId(), source.title(),
                            source.pageId()));
                }
            }
            if (drafts.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "AI couldn’t make flashcards from this content. Try another part of the course.");
            }
            String title = request.title() == null || request.title().isBlank() ? scope.title() : request.title().strip();
            return store.create(accountId, lmsCourseId, title, drafts, today());
        }, set -> false);
    }

    private record Scope(String title, Long topicId, List<AiServiceMessages.SourceRef> allowed) {
    }

    private static Scope scope(AiCourseKnowledge knowledge, AiFlashcardSetRequest request) {
        if (request.scope() == AiFlashcardSetRequest.Scope.COURSE) {
            return new Scope("Course flashcards", null, knowledge.allowedSources());
        }
        long id = request.id() == null ? 0 : request.id();
        return switch (request.scope()) {
            case TOPIC -> new Scope(knowledge.topic(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                    AiStudyService.TOPIC_UNAVAILABLE)).title(), id, knowledge.allowedSources());
            case MODULE -> {
                if (!knowledge.hasModule(id)) {
                    throw new ApiException(HttpStatus.NOT_FOUND, AiStudyService.MODULE_UNAVAILABLE);
                }
                yield new Scope("Module flashcards", null, knowledge.allowedSourcesInModule(id));
            }
            default -> new Scope(knowledge.material(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                    AiStudyService.FILE_UNAVAILABLE)).title(), null, List.of(new AiServiceMessages.SourceRef("STUDY_MATERIAL", id)));
        };
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(college.zone()));
    }

    private void requireCourse(Long accountId, Long lmsCourseId) {
        if (!studentAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }
}
