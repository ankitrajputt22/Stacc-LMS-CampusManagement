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
import com.stacc.backend.lms.ai.AiServiceMessages.AskRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.AskResult;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeQuestion;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeResult;
import com.stacc.backend.lms.ai.AiServiceMessages.SummaryRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.SummaryResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The student AI study assistant: grounded questions, topic summaries, and practice questions for a course the
 * student may open right now. Access is decided by {@link LmsStudentAccessService} on every request; a course the
 * student may not use answers the same 404 as a missing one. Only the question, the generation settings, an opaque
 * course key, and the content fingerprint go to the AI service, never anything about the student, and nothing is
 * saved.
 */
@Service
public class AiStudyService {

    static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    static final String TOPIC_UNAVAILABLE = "Topic not found or unavailable.";
    static final String QUESTION_LENGTH = "Enter a question of 2 to 1,000 characters.";

    private final LmsStudentAccessService studentAccess;
    private final AiKnowledgeSources sources;
    private final AiKnowledgeRecords records;
    private final AiServiceClient client;

    public AiStudyService(LmsStudentAccessService studentAccess, AiKnowledgeSources sources, AiKnowledgeRecords records,
            AiServiceClient client) {
        this.studentAccess = studentAccess;
        this.sources = sources;
        this.records = records;
        this.client = client;
    }

    public AiAnswerResponse ask(Long accountId, Long lmsCourseId, AiAskRequest request) {
        String question = request.question() == null ? "" : request.question().strip();
        if (question.length() < 2 || question.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, QUESTION_LENGTH);
        }
        AiCourseKnowledge knowledge = readyKnowledge(accountId, lmsCourseId);
        AskResult result = call(() -> client.ask(AiCourseKeys.forLmsCourse(lmsCourseId),
                new AskRequest(knowledge.fingerprint(), question)));
        List<AiCitationResponse> citations = AiResults.citations(result.citations(), knowledge);
        String answer = AiResults.text(result.answer(), AiResults.ANSWER_MAX);
        // An answer is shown only with at least one real citation; anything else is insufficient context.
        if (!Boolean.FALSE.equals(result.insufficientContext()) || answer == null || citations.isEmpty()) {
            return AiAnswerResponse.insufficient();
        }
        return new AiAnswerResponse(answer, false, citations);
    }

    public AiTopicSummaryResponse summarize(Long accountId, Long lmsCourseId, Long topicId) {
        AiCourseKnowledge knowledge = readyKnowledge(accountId, lmsCourseId, topicId);
        AiCourseKnowledge.Topic topic = knowledge.topic(topicId).orElseThrow();
        SummaryResult result = call(() -> client.summarize(AiCourseKeys.forLmsCourse(lmsCourseId),
                new SummaryRequest(knowledge.fingerprint(), topicId)));
        List<AiCitationResponse> citations = AiResults.citations(result.citations(), knowledge);
        String summary = AiResults.text(result.summary(), AiResults.SUMMARY_MAX);
        if (!Boolean.FALSE.equals(result.insufficientContext()) || summary == null || citations.isEmpty()) {
            return new AiTopicSummaryResponse(topicId, topic.title(), null, List.of(), true, List.of());
        }
        return new AiTopicSummaryResponse(topicId, topic.title(), summary,
                AiResults.texts(result.keyPoints(), AiResults.KEY_POINT_MAX, AiResults.MAX_KEY_POINTS), false, citations);
    }

    public AiPracticeResponse practice(Long accountId, Long lmsCourseId, AiPracticeRequest request) {
        int count = request.questionCountOrDefault();
        AiCourseKnowledge knowledge = readyKnowledge(accountId, lmsCourseId, request.topicId());
        PracticeResult result = call(() -> client.practice(AiCourseKeys.forLmsCourse(lmsCourseId),
                new PracticeRequest(knowledge.fingerprint(), count, request.topicId())));
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
        return new AiPracticeResponse(List.copyOf(questions), questions.isEmpty());
    }

    private AiCourseKnowledge readyKnowledge(Long accountId, Long lmsCourseId) {
        return readyKnowledge(accountId, lmsCourseId, null);
    }

    /**
     * The course's published knowledge, once the student may use the course, AI is on, the topic (if any) is one the
     * student can see in this course, and the index matches the current published content.
     */
    private AiCourseKnowledge readyKnowledge(Long accountId, Long lmsCourseId, Long topicId) {
        if (!studentAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
        if (!client.isEnabled()) {
            throw AiErrors.unavailable();
        }
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

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (AiServiceException exception) {
            throw AiErrors.of(exception, AiErrors.STUDY_NOT_READY);
        }
    }
}
