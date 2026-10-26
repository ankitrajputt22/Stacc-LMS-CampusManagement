package com.stacc.backend.lms.ai.api;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.ai.AiCourseKeys;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiCourseKnowledgeIndex;
import com.stacc.backend.lms.ai.AiErrors;
import com.stacc.backend.lms.ai.AiKnowledgeIndexStatus;
import com.stacc.backend.lms.ai.AiKnowledgeRecords;
import com.stacc.backend.lms.ai.AiKnowledgeRefresher;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.AiServiceException;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentDraftRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentDraftResult;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentQuestion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Faculty AI tools for a course the faculty member teaches, checked with {@link LmsFacultyAccessService} on every
 * request: the course's AI knowledge status, refreshing it, and generating an assessment draft. A draft is only shown
 * to the teacher: nothing here creates, publishes, or grades an assignment, and nothing is saved.
 */
@Service
public class AiFacultyService {

    static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    private static final Set<String> QUESTION_TYPES = Set.of("MCQ", "SHORT_ANSWER", "LONG_ANSWER");
    private static final BigDecimal MAX_MARKS = new BigDecimal("100");

    private final LmsFacultyAccessService facultyAccess;
    private final AiKnowledgeSources sources;
    private final AiKnowledgeRecords records;
    private final AiKnowledgeRefresher refresher;
    private final AiServiceClient client;

    public AiFacultyService(LmsFacultyAccessService facultyAccess, AiKnowledgeSources sources,
            AiKnowledgeRecords records, AiKnowledgeRefresher refresher, AiServiceClient client) {
        this.facultyAccess = facultyAccess;
        this.sources = sources;
        this.records = records;
        this.refresher = refresher;
        this.client = client;
    }

    public AiKnowledgeStatusResponse status(Long accountId, Long lmsCourseId) {
        requireAccess(accountId, lmsCourseId);
        AiCourseKnowledge knowledge = sources.publishedKnowledge(lmsCourseId);
        Optional<AiCourseKnowledgeIndex> index = records.find(lmsCourseId);
        if (index.isEmpty()) {
            return new AiKnowledgeStatusResponse(client.isEnabled(), "NOT_INDEXED", false, null,
                    knowledge.publishedSourceCount(), null);
        }
        AiCourseKnowledgeIndex row = index.get();
        return new AiKnowledgeStatusResponse(client.isEnabled(), row.getStatus().name(),
                row.isStaleFor(knowledge.fingerprint()), row.getIndexedAt(), knowledge.publishedSourceCount(),
                row.getStatus() == AiKnowledgeIndexStatus.FAILED ? row.getLastError() : null);
    }

    public AiKnowledgeSyncResponse sync(Long accountId, Long lmsCourseId) {
        requireAccess(accountId, lmsCourseId);
        AiKnowledgeRefresher.Outcome outcome = refresher.refresh(lmsCourseId);
        return new AiKnowledgeSyncResponse(AiKnowledgeIndexStatus.READY.name(), outcome.indexedSourceCount(),
                outcome.skippedSourceCount(), outcome.indexedAt());
    }

    public AiAssessmentDraftResponse assessmentDraft(Long accountId, Long lmsCourseId, AiAssessmentDraftRequest request) {
        requireAccess(accountId, lmsCourseId);
        if (!client.isEnabled()) {
            throw AiErrors.unavailable();
        }
        AiCourseKnowledge knowledge = sources.publishedKnowledge(lmsCourseId);
        boolean ready = records.find(lmsCourseId).map(index -> index.answersFor(knowledge.fingerprint())).orElse(false);
        if (!ready) {
            throw new ApiException(HttpStatus.CONFLICT, AiErrors.TEACHING_NOT_READY);
        }
        String note = AiResults.text(request.additionalInstruction(), 500);
        AssessmentDraftResult result;
        try {
            result = client.assessmentDraft(AiCourseKeys.forLmsCourse(lmsCourseId), new AssessmentDraftRequest(
                    knowledge.fingerprint(), request.questionCount(), request.difficulty().name(),
                    request.questionType().name(), note));
        } catch (AiServiceException exception) {
            throw AiErrors.of(exception, AiErrors.TEACHING_NOT_READY);
        }
        List<AiAssessmentQuestionResponse> questions = questions(result, knowledge, request);
        if (questions.isEmpty()) {
            return new AiAssessmentDraftResponse(null, null, List.of(), true);
        }
        return new AiAssessmentDraftResponse(AiResults.text(result.titleSuggestion(), AiResults.TITLE_MAX),
                AiResults.text(result.instructionsSuggestion(), AiResults.INSTRUCTIONS_MAX), questions, false);
    }

    /** Only well-formed, cited questions of the requested kind survive, at most as many as were asked for. */
    private static List<AiAssessmentQuestionResponse> questions(AssessmentDraftResult result, AiCourseKnowledge knowledge,
            AiAssessmentDraftRequest request) {
        List<AiAssessmentQuestionResponse> accepted = new ArrayList<>();
        if (result.questions() == null) {
            return accepted;
        }
        for (AssessmentQuestion question : result.questions()) {
            if (question == null || accepted.size() >= request.questionCount()) {
                continue;
            }
            String type = question.questionType();
            boolean typeFits = type != null && QUESTION_TYPES.contains(type)
                    && (request.questionType() == AiQuestionType.MIXED || request.questionType().name().equals(type));
            String prompt = AiResults.text(question.prompt(), AiResults.QUESTION_MAX);
            String answer = AiResults.text(question.suggestedAnswer(), AiResults.SUGGESTED_ANSWER_MAX);
            List<String> options = "MCQ".equals(type)
                    ? AiResults.texts(question.options(), AiResults.OPTION_MAX, AiResults.MAX_OPTIONS) : List.of();
            BigDecimal marks = question.suggestedMarks() == null ? null
                    : question.suggestedMarks().setScale(2, RoundingMode.HALF_UP);
            List<AiCitationResponse> citations = AiResults.citations(question.citations(), knowledge);
            if (!typeFits || prompt == null || answer == null || ("MCQ".equals(type) && options.size() < 2)
                    || marks == null || marks.signum() <= 0 || marks.compareTo(MAX_MARKS) > 0 || citations.isEmpty()) {
                continue;
            }
            accepted.add(new AiAssessmentQuestionResponse(type, prompt, options, answer, marks, citations));
        }
        return List.copyOf(accepted);
    }

    private void requireAccess(Long accountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }
}
