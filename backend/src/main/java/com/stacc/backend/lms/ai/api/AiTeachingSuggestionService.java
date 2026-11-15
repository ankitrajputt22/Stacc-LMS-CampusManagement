package com.stacc.backend.lms.ai.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.ai.AiCourseKeys;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiErrors;
import com.stacc.backend.lms.ai.AiKnowledgeRecords;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.AiServiceException;
import com.stacc.backend.lms.ai.AiServiceMessages.ModelAnswerRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.ModelAnswerResult;
import com.stacc.backend.lms.ai.AiServiceMessages.RubricCriterion;
import com.stacc.backend.lms.ai.AiServiceMessages.RubricRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.RubricResult;
import com.stacc.backend.lms.ai.control.AiRequestGate;
import com.stacc.backend.lms.ai.control.AiRequestLogEntry;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentManagementService;
import com.stacc.backend.lms.assignment.rubric.LmsRubric;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionBankService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Answers and rubrics (v0.14.5): a model answer to one bank question on request, and a rubric suggestion for one
 * assignment, both from the course's published content and both only shown to the teacher. Nothing is saved here: the
 * teacher saves through the normal question edit or rubric form, under their own rules.
 */
@Service
public class AiTeachingSuggestionService {

    static final int MAX_LEVELS = 4;

    private final LmsQuestionBankService bank;
    private final LmsAssignmentManagementService assignments;
    private final AiKnowledgeSources sources;
    private final AiKnowledgeRecords records;
    private final AiServiceClient client;
    private final AiRequestGate gate;

    public AiTeachingSuggestionService(LmsQuestionBankService bank, LmsAssignmentManagementService assignments,
            AiKnowledgeSources sources, AiKnowledgeRecords records, AiServiceClient client, AiRequestGate gate) {
        this.bank = bank;
        this.assignments = assignments;
        this.sources = sources;
        this.records = records;
        this.client = client;
        this.gate = gate;
    }

    public AiModelAnswerResponse modelAnswer(Long accountId, Long questionId) {
        LmsQuestion question = bank.find(accountId, questionId);
        Long lmsCourseId = question.getLmsCourse().getId();
        return gate.run(AiRequestLogEntry.Kind.MODEL_ANSWER, accountId, lmsCourseId, () -> {
            AiCourseKnowledge knowledge = readyKnowledge(lmsCourseId);
            ModelAnswerResult result = call(() -> client.modelAnswer(AiCourseKeys.forLmsCourse(lmsCourseId), new ModelAnswerRequest(
                    knowledge.fingerprint(), cut(question.getText(), 5000), question.getType().name(), question.getMarks(),
                    knowledge.allowedSources())));
            List<AiCitationResponse> citations = AiResults.citations(result.citations(), knowledge);
            String answer = AiResults.text(result.answer(), 4000);
            if (!Boolean.FALSE.equals(result.insufficientContext()) || answer == null || citations.isEmpty()) {
                return new AiModelAnswerResponse(null, List.of(), List.of(), true);
            }
            return new AiModelAnswerResponse(answer, AiResults.texts(result.keyPoints(), 300, 8), citations, false);
        }, AiModelAnswerResponse::insufficientContext);
    }

    public AiRubricSuggestionResponse rubric(Long accountId, Long assignmentId, int criteriaCount) {
        LmsAssignment assignment = assignments.find(accountId, assignmentId);
        Long lmsCourseId = assignment.getLmsCourse().getId();
        BigDecimal maxMarks = assignment.getMaxMarks();
        return gate.run(AiRequestLogEntry.Kind.RUBRIC, accountId, lmsCourseId, () -> {
            AiCourseKnowledge knowledge = readyKnowledge(lmsCourseId);
            RubricResult result = call(() -> client.rubric(AiCourseKeys.forLmsCourse(lmsCourseId), new RubricRequest(
                    knowledge.fingerprint(), cut(assignment.getTitle(), 200), cut(assignment.getInstructions(), 20_000), maxMarks,
                    criteriaCount, knowledge.allowedSources())));
            List<AiRubricSuggestionResponse.Criterion> criteria = criteria(result, maxMarks);
            String title = cut("Rubric: " + assignment.getTitle(), LmsRubric.MAX_TITLE_LENGTH);
            if (criteria.isEmpty()) {
                return new AiRubricSuggestionResponse(title, maxMarks, List.of(), List.of(), true);
            }
            return new AiRubricSuggestionResponse(title, maxMarks, criteria, AiResults.citations(result.citations(), knowledge), false);
        }, AiRubricSuggestionResponse::insufficientContext);
    }

    /**
     * The suggested criteria as the rubric form needs them, or none: points rounded to 2 places, the last criterion
     * absorbing any rounding so the total is exactly the assignment's maximum, levels capped at their criterion.
     */
    static List<AiRubricSuggestionResponse.Criterion> criteria(RubricResult result, BigDecimal maxMarks) {
        if (result == null || result.criteria() == null || Boolean.TRUE.equals(result.insufficientContext())) {
            return List.of();
        }
        List<RubricCriterion> given = result.criteria().stream()
                .filter(criterion -> criterion != null && AiResults.text(criterion.title(), 200) != null
                        && criterion.maxPoints() != null && criterion.maxPoints().signum() > 0)
                .limit(LmsRubric.MAX_CRITERIA).toList();
        if (given.isEmpty()) {
            return List.of();
        }
        List<AiRubricSuggestionResponse.Criterion> made = new ArrayList<>();
        BigDecimal used = BigDecimal.ZERO;
        for (int index = 0; index < given.size(); index++) {
            RubricCriterion criterion = given.get(index);
            BigDecimal points = index == given.size() - 1 ? maxMarks.subtract(used)
                    : criterion.maxPoints().setScale(2, RoundingMode.HALF_UP);
            if (points.signum() <= 0) {
                return List.of();
            }
            used = used.add(points);
            BigDecimal cap = points;
            List<AiRubricSuggestionResponse.Level> levels = criterion.levels() == null ? List.of() : criterion.levels().stream()
                    .filter(level -> level != null && AiResults.text(level.title(), 100) != null && level.points() != null
                            && level.points().signum() >= 0)
                    .limit(MAX_LEVELS)
                    .map(level -> new AiRubricSuggestionResponse.Level(AiResults.text(level.title(), 100),
                            AiResults.text(level.description(), 500), level.points().min(cap).setScale(2, RoundingMode.HALF_UP)))
                    .toList();
            made.add(new AiRubricSuggestionResponse.Criterion(AiResults.text(criterion.title(), 200),
                    AiResults.text(criterion.description(), 1000), points, levels));
        }
        return used.compareTo(maxMarks) == 0 ? List.copyOf(made) : List.of();
    }

    private AiCourseKnowledge readyKnowledge(Long lmsCourseId) {
        if (!client.isEnabled()) {
            throw AiErrors.unavailable();
        }
        AiCourseKnowledge knowledge = sources.publishedKnowledge(lmsCourseId);
        if (!records.find(lmsCourseId).map(index -> index.answersFor(knowledge.fingerprint())).orElse(false)) {
            throw new ApiException(HttpStatus.CONFLICT, AiErrors.TEACHING_NOT_READY);
        }
        return knowledge;
    }

    private static <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (AiServiceException exception) {
            throw AiErrors.of(exception, AiErrors.TEACHING_NOT_READY);
        }
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
