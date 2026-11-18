package com.stacc.backend.lms.ai.api;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.ai.AiCourseKeys;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiErrors;
import com.stacc.backend.lms.ai.AiKnowledgeRecords;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.AiServiceException;
import com.stacc.backend.lms.ai.AiServiceMessages;
import com.stacc.backend.lms.ai.AiServiceMessages.AskRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.IndexRef;
import com.stacc.backend.lms.ai.control.AiCourseControlService;
import com.stacc.backend.lms.ai.control.AiQuizPause;
import com.stacc.backend.lms.ai.mastery.AdaptiveChoice;
import com.stacc.backend.lms.ai.mastery.AiLearningEvidence;
import com.stacc.backend.lms.ai.mastery.AiMasteryService;
import com.stacc.backend.lms.assignment.AssignmentSubmissionWindow;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import java.time.Clock;
import com.stacc.backend.lms.ai.control.AiRequestGate;
import com.stacc.backend.lms.ai.control.AiRequestLogEntry;
import com.stacc.backend.lms.ai.notes.AiStudentNoteIndexer;
import com.stacc.backend.lms.ai.AiServiceMessages.AskResult;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeQuestion;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeResult;
import com.stacc.backend.lms.ai.AiServiceMessages.SummaryRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.SummaryResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiStudyService {

    static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    static final String TOPIC_UNAVAILABLE = "Topic not found or unavailable.";
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    static final String QUESTION_LENGTH = "Enter a question of 2 to 1,000 characters.";
    static final String MODULE_UNAVAILABLE = "Module not found or unavailable.";
    static final String FILE_UNAVAILABLE = "File not found or unavailable.";
    static final String ASSIGNMENT_UNAVAILABLE = "Assignment not found or unavailable.";
    static final String ASSIGNMENT_NOT_OPEN = "This assignment is not taking work now, so there is nothing to help with.";
    static final String MODULE_WITH_NOTES_ONLY = "Choose a module only when asking about the course.";

    private final LmsStudentAccessService studentAccess;
    private final AiKnowledgeSources sources;
    private final AiKnowledgeRecords records;
    private final AiServiceClient client;
    private final AiStudentNoteIndexer notes;
    private final AiCourseControlService controls;
    private final AiRequestGate gate;
    private final AiQuizPause quizPause;
    private final AiMasteryService mastery;
    private final LmsAssignmentRepository assignments;
    private final Clock clock;

    public AiStudyService(LmsStudentAccessService studentAccess, AiKnowledgeSources sources, AiKnowledgeRecords records,
            AiServiceClient client, AiStudentNoteIndexer notes, AiCourseControlService controls, AiRequestGate gate,
            AiQuizPause quizPause, LmsAssignmentRepository assignments, Clock clock, AiMasteryService mastery) {
        this.mastery = mastery;
        this.quizPause = quizPause;
        this.assignments = assignments;
        this.clock = clock;
        this.notes = notes;
        this.controls = controls;
        this.gate = gate;
        this.studentAccess = studentAccess;
        this.sources = sources;
        this.records = records;
        this.client = client;
    }

    public AiAnswerResponse ask(Long accountId, Long lmsCourseId, AiAskRequest request) {
        return gate.run(AiRequestLogEntry.Kind.ASK, accountId, lmsCourseId, () -> answer(accountId, lmsCourseId, request),
                AiAnswerResponse::insufficientContext);
    }

    private AiAnswerResponse answer(Long accountId, Long lmsCourseId, AiAskRequest request) {
        String question = request.question() == null ? "" : request.question().strip();
        if (question.length() < 2 || question.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, QUESTION_LENGTH);
        }
        AiAskMode mode = request.effectiveMode();
        boolean hintOnly = request.assignmentId() != null && openAssignment(lmsCourseId, request.assignmentId());
        if (mode == AiAskMode.NOTES_ONLY && request.moduleId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MODULE_WITH_NOTES_ONLY);
        }
        AiCourseKnowledge knowledge = readyKnowledge(accountId, lmsCourseId);
        if (request.moduleId() != null && !knowledge.hasModule(request.moduleId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, MODULE_UNAVAILABLE);
        }
        List<AiServiceMessages.SourceRef> courseSources = mode == AiAskMode.NOTES_ONLY ? List.of()
                : request.moduleId() != null ? knowledge.allowedSourcesInModule(request.moduleId()) : knowledge.allowedSources();
        Optional<AiStudentNoteIndexer.Scope> myNotes = request.withMyNotes()
                ? call(() -> notes.current(lmsCourseId, accountId)) : Optional.empty();
        if (mode == AiAskMode.NOTES_ONLY && myNotes.isEmpty()) {
            return AiAnswerResponse.insufficient();
        }
        List<IndexRef> additional = myNotes.map(scope -> List.of(scope.index())).orElse(List.of());
        AskResult result = call(() -> client.ask(AiCourseKeys.forLmsCourse(lmsCourseId),
                new AskRequest(knowledge.fingerprint(), question, courseSources, additional, turns(request),
                        hintOnly ? "HINT" : request.style() == null ? AiAnswerStyle.NORMAL.name() : request.style().name())));
        List<AiCitationResponse> citations = AiResults.citations(result.citations(), knowledge, null,
                myNotes.map(AiStudentNoteIndexer.Scope::knowledge).orElse(null));
        String answer = AiResults.text(result.answer(), AiResults.ANSWER_MAX);
        if (!Boolean.FALSE.equals(result.insufficientContext()) || answer == null || citations.isEmpty()) {
            return AiAnswerResponse.insufficient();
        }
        return new AiAnswerResponse(answer, false, citations);
    }

    public AiTopicSummaryResponse summarize(Long accountId, Long lmsCourseId, Long topicId) {
        return gate.run(AiRequestLogEntry.Kind.SUMMARY, accountId, lmsCourseId, () -> summary(accountId, lmsCourseId, topicId),
                AiTopicSummaryResponse::insufficientContext);
    }

    private AiTopicSummaryResponse summary(Long accountId, Long lmsCourseId, Long topicId) {
        AiCourseKnowledge knowledge = readyKnowledge(accountId, lmsCourseId, topicId);
        AiCourseKnowledge.Topic topic = knowledge.topic(topicId).orElseThrow();
        SummaryResult result = call(() -> client.summarize(AiCourseKeys.forLmsCourse(lmsCourseId),
                new SummaryRequest(knowledge.fingerprint(), topicId, knowledge.allowedSources())));
        List<AiCitationResponse> citations = AiResults.citations(result.citations(), knowledge);
        String summary = AiResults.text(result.summary(), AiResults.SUMMARY_MAX);
        if (!Boolean.FALSE.equals(result.insufficientContext()) || summary == null || citations.isEmpty()) {
            return new AiTopicSummaryResponse(topicId, topic.title(), null, List.of(), true, List.of());
        }
        return new AiTopicSummaryResponse(topicId, topic.title(), summary,
                AiResults.texts(result.keyPoints(), AiResults.KEY_POINT_MAX, AiResults.MAX_KEY_POINTS), false, citations);
    }

    public AiSummaryResponse summarize(Long accountId, Long lmsCourseId, AiSummaryRequest request) {
        return gate.run(AiRequestLogEntry.Kind.SUMMARY, accountId, lmsCourseId, () -> scopedSummary(accountId, lmsCourseId, request),
                AiSummaryResponse::insufficientContext);
    }

    private AiSummaryResponse scopedSummary(Long accountId, Long lmsCourseId, AiSummaryRequest request) {
        AiSummaryStyle style = request.style() == null ? AiSummaryStyle.BRIEF : request.style();
        long id = request.id();
        AiCourseKnowledge knowledge = readyKnowledge(accountId, lmsCourseId);
        String title;
        Long topicId = null;
        List<AiServiceMessages.SourceRef> allowed;
        switch (request.scope()) {
            case TOPIC -> {
                title = knowledge.topic(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, TOPIC_UNAVAILABLE)).title();
                topicId = id;
                allowed = knowledge.allowedSources();
            }
            case MODULE -> {
                if (!knowledge.hasModule(id)) {
                    throw new ApiException(HttpStatus.NOT_FOUND, MODULE_UNAVAILABLE);
                }
                title = null;
                allowed = knowledge.allowedSourcesInModule(id);
            }
            default -> {
                title = knowledge.material(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, FILE_UNAVAILABLE)).title();
                allowed = List.of(new AiServiceMessages.SourceRef("STUDY_MATERIAL", id));
            }
        }
        Long scopeTopic = topicId;
        SummaryResult result = call(() -> client.summarize(AiCourseKeys.forLmsCourse(lmsCourseId),
                new SummaryRequest(knowledge.fingerprint(), scopeTopic, allowed, List.of(), style.name())));
        List<AiCitationResponse> citations = AiResults.citations(result.citations(), knowledge);
        String summary = AiResults.text(result.summary(), AiResults.SUMMARY_MAX);
        if (!Boolean.FALSE.equals(result.insufficientContext()) || summary == null || citations.isEmpty()) {
            return new AiSummaryResponse(request.scope(), id, title, style, null, List.of(), true, List.of());
        }
        return new AiSummaryResponse(request.scope(), id, title, style, summary,
                AiResults.texts(result.keyPoints(), AiResults.KEY_POINT_MAX, AiResults.MAX_KEY_POINTS), false, citations);
    }

    public AiPracticeResponse practice(Long accountId, Long lmsCourseId, AiPracticeRequest request) {
        return gate.run(AiRequestLogEntry.Kind.PRACTICE, accountId, lmsCourseId, () -> practiceSet(accountId, lmsCourseId, request),
                AiPracticeResponse::insufficientContext);
    }

    private AiPracticeResponse practiceSet(Long accountId, Long lmsCourseId, AiPracticeRequest request) {
        int count = request.questionCountOrDefault();
        AdaptiveChoice.Choice choice = Boolean.TRUE.equals(request.adaptive())
                ? AdaptiveChoice.choose(mastery.view(accountId, lmsCourseId), RANDOM).orElse(null) : null;
        Long topicId = choice != null ? Long.valueOf(choice.topicId()) : Boolean.TRUE.equals(request.adaptive()) ? null : request.topicId();
        AiCourseKnowledge knowledge = readyKnowledge(accountId, lmsCourseId, topicId);
        PracticeResult result = call(() -> client.practice(AiCourseKeys.forLmsCourse(lmsCourseId),
                new PracticeRequest(knowledge.fingerprint(), count, topicId, knowledge.allowedSources())));
        List<AiPracticeQuestionResponse> questions = new ArrayList<>();
        if (result.questions() != null) {
            for (PracticeQuestion question : result.questions()) {
                if (question == null || questions.size() >= count) {
                    continue;
                }
                String text = AiResults.text(question.question(), AiResults.QUESTION_MAX);
                String answer = AiResults.text(question.suggestedAnswer(), AiResults.SUGGESTED_ANSWER_MAX);
                List<AiCitationResponse> citations = AiResults.citations(question.citations(), knowledge);
                if (text != null && answer != null && !citations.isEmpty()) {
                    questions.add(new AiPracticeQuestionResponse(text, answer, citations));
                }
            }
        }
        return new AiPracticeResponse(List.copyOf(questions), questions.isEmpty(), choice == null ? null : choice.topicId(),
                choice == null ? null : choice.reason());
    }

    private AiCourseKnowledge readyKnowledge(Long accountId, Long lmsCourseId) {
        return readyKnowledge(accountId, lmsCourseId, null);
    }

    private AiCourseKnowledge readyKnowledge(Long accountId, Long lmsCourseId, Long topicId) {
        if (!studentAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
        if (!client.isEnabled()) {
            throw AiErrors.unavailable();
        }
        controls.requireStudentAi(lmsCourseId);
        quizPause.requireNoQuizInProgress(accountId, lmsCourseId);
        AiCourseKnowledge knowledge = sources.publishedKnowledge(lmsCourseId);
        if (topicId != null && knowledge.topic(topicId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, TOPIC_UNAVAILABLE);
        }
        boolean ready = records.find(lmsCourseId).map(index -> index.answersFor(knowledge.fingerprint())).orElse(false);
        if (!ready) {
            throw new ApiException(HttpStatus.CONFLICT, AiErrors.STUDY_NOT_READY);
        }
        return knowledge;
    }

    private boolean openAssignment(Long lmsCourseId, Long assignmentId) {
        LmsAssignment assignment = assignments.findById(assignmentId)
                .filter(found -> found.getLmsCourse().getId().equals(lmsCourseId) && found.getStatus() == LmsContentStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_UNAVAILABLE));
        if (assignment.submissionWindowAt(clock.instant()) != AssignmentSubmissionWindow.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, ASSIGNMENT_NOT_OPEN);
        }
        return true;
    }

    public AiEvaluateResponse evaluate(Long accountId, Long lmsCourseId, AiEvaluateRequest request) {
        return gate.run(AiRequestLogEntry.Kind.EVALUATE, accountId, lmsCourseId, () -> {
            AiCourseKnowledge knowledge = readyKnowledge(accountId, lmsCourseId);
            AiEvaluateRequest.Mode mode = request.mode() == null ? AiEvaluateRequest.Mode.PRACTICE : request.mode();
            AiServiceMessages.EvaluateResult result = call(() -> client.evaluate(AiCourseKeys.forLmsCourse(lmsCourseId),
                    new AiServiceMessages.EvaluateRequest(knowledge.fingerprint(), request.question(), request.referenceAnswer(),
                            request.studentAnswer(), request.maxMarks(), mode.name(), knowledge.allowedSources())));
            List<AiCitationResponse> citations = AiResults.citations(result.citations(), knowledge);
            String feedback = AiResults.text(result.feedback(), 2000);
            if (!Boolean.FALSE.equals(result.insufficientContext()) || feedback == null || citations.isEmpty() || result.marks() == null) {
                return new AiEvaluateResponse(null, request.maxMarks(), null, null, null, List.of(), true);
            }
            java.math.BigDecimal marks = result.marks().max(java.math.BigDecimal.ZERO)
                    .min(java.math.BigDecimal.valueOf(request.maxMarks())).setScale(1, java.math.RoundingMode.HALF_UP);
            mastery.record(accountId, lmsCourseId, citations.get(0).pageId(), AiLearningEvidence.Kind.PRACTICE_ANSWER,
                    marks.doubleValue() / request.maxMarks());
            String verdict = java.util.Set.of("CORRECT", "PARTLY_CORRECT", "INCORRECT").contains(result.verdict()) ? result.verdict() : null;
            return new AiEvaluateResponse(marks, request.maxMarks(), verdict, feedback,
                    mode == AiEvaluateRequest.Mode.VIVA ? AiResults.text(result.followUpQuestion(), 500) : null, citations, false);
        }, AiEvaluateResponse::insufficientContext);
    }

    private static List<AiServiceMessages.Turn> turns(AiAskRequest request) {
        if (request.history() == null) {
            return List.of();
        }
        return request.history().stream().filter(turn -> turn != null && AiResults.text(turn.question(), 1000) != null
                        && AiResults.text(turn.answer(), 2000) != null)
                .limit(3)
                .map(turn -> new AiServiceMessages.Turn(AiResults.text(turn.question(), 1000), AiResults.text(turn.answer(), 2000)))
                .toList();
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (AiServiceException exception) {
            throw AiErrors.of(exception, AiErrors.STUDY_NOT_READY);
        }
    }
}
