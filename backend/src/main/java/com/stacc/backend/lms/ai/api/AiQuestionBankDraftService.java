package com.stacc.backend.lms.ai.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.ai.AiCourseKeys;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiErrors;
import com.stacc.backend.lms.ai.AiIndexKind;
import com.stacc.backend.lms.ai.AiKnowledgeRecords;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.AiServiceException;
import com.stacc.backend.lms.ai.AiServiceMessages.BankDraftRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.BankDraftResult;
import com.stacc.backend.lms.ai.AiServiceMessages.BankQuestion;
import com.stacc.backend.lms.ai.AiServiceMessages.ExistingQuestion;
import com.stacc.backend.lms.ai.AiServiceMessages.QualityChecks;
import com.stacc.backend.lms.ai.AiServiceMessages.IndexRef;
import com.stacc.backend.lms.ai.control.AiRequestGate;
import com.stacc.backend.lms.ai.control.AiRequestLogEntry;
import com.stacc.backend.lms.ai.teacher.AiTeacherDocumentService;
import com.stacc.backend.lms.ai.teacher.AiTeacherKnowledge;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.question.LmsQuestionAiChecks;
import com.stacc.backend.lms.question.LmsQuestionBankService;
import com.stacc.backend.lms.question.LmsQuestionContent;
import com.stacc.backend.lms.question.LmsQuestionSource;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiQuestionBankDraftService {

    static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    static final String TOPIC_UNAVAILABLE = "Topic not found or unavailable.";
    private static final BigDecimal MAX_MARKS = new BigDecimal("100");
    static final int MAX_EXISTING = 500;

    private final LmsFacultyAccessService facultyAccess;
    private final AiKnowledgeSources sources;
    private final AiKnowledgeRecords records;
    private final AiServiceClient client;
    private final AiTeacherDocumentService teacherDocuments;
    private final LmsQuestionBankService bank;
    private final AiRequestGate gate;

    public AiQuestionBankDraftService(LmsFacultyAccessService facultyAccess, AiKnowledgeSources sources,
            AiKnowledgeRecords records, AiServiceClient client, AiTeacherDocumentService teacherDocuments,
            LmsQuestionBankService bank, AiRequestGate gate) {
        this.facultyAccess = facultyAccess;
        this.sources = sources;
        this.records = records;
        this.client = client;
        this.teacherDocuments = teacherDocuments;
        this.bank = bank;
        this.gate = gate;
    }

    public record Labels(String topic, BloomLevel bloomLevel, Long outcomeId) {

        static final Labels NONE = new Labels(null, null, null);
    }

    public AiQuestionBankDraftResponse draft(Long accountId, Long lmsCourseId, AiQuestionBankDraftRequest request) {
        return draft(accountId, lmsCourseId, request, Labels.NONE);
    }

    public AiQuestionBankDraftResponse draft(Long accountId, Long lmsCourseId, AiQuestionBankDraftRequest request, Labels labels) {
        return gate.run(AiRequestLogEntry.Kind.QUESTION_BANK, accountId, lmsCourseId,
                () -> generate(accountId, lmsCourseId, request, labels), AiQuestionBankDraftResponse::insufficientContext);
    }

    private AiQuestionBankDraftResponse generate(Long accountId, Long lmsCourseId, AiQuestionBankDraftRequest request, Labels labels) {
        if (!facultyAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
        if (!client.isEnabled()) {
            throw AiErrors.unavailable();
        }
        AiCourseKnowledge knowledge = sources.publishedKnowledge(lmsCourseId);
        if (request.topicId() != null && knowledge.topic(request.topicId()).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, TOPIC_UNAVAILABLE);
        }
        if (!records.find(lmsCourseId).map(index -> index.answersFor(knowledge.fingerprint())).orElse(false)) {
            throw new ApiException(HttpStatus.CONFLICT, AiErrors.TEACHING_NOT_READY);
        }
        AiTeacherKnowledge teacher = teacherDocuments.knowledge(lmsCourseId);
        boolean withTeacher = !teacher.isEmpty() && records.find(lmsCourseId, AiIndexKind.TEACHER_DOCUMENTS)
                .map(index -> index.answersFor(teacher.fingerprint())).orElse(false);
        List<IndexRef> additional = withTeacher && request.topicId() == null
                ? List.of(new IndexRef(AiCourseKeys.forTeacherDocuments(lmsCourseId), teacher.fingerprint(), teacher.allowedSources()))
                : List.of();
        List<String> types = new LinkedHashSet<>(request.questionTypes()).stream().map(Enum::name).toList();
        List<ExistingQuestion> existing = existing(lmsCourseId);
        java.util.Set<Long> sent = new java.util.HashSet<>();
        existing.forEach(question -> sent.add(question.id()));
        BankDraftResult result;
        try {
            result = client.questionBankDraft(AiCourseKeys.forLmsCourse(lmsCourseId), new BankDraftRequest(
                    knowledge.fingerprint(), request.questionCount(), types, request.difficulty().name(), request.topicId(),
                    AiResults.text(request.additionalInstruction(), 500), knowledge.allowedSources(), additional, existing));
        } catch (AiServiceException exception) {
            throw AiErrors.of(exception, AiErrors.TEACHING_NOT_READY);
        }
        int dropped = result.droppedForKeyMismatch() == null ? 0 : Math.max(0, result.droppedForKeyMismatch());
        int repeats = result.droppedAsRepeats() == null ? 0 : Math.max(0, result.droppedAsRepeats());
        List<LmsQuestionBankService.AiDraft> drafts = new ArrayList<>();
        int leftOut = 0;
        for (BankQuestion question : result.questions() == null ? List.<BankQuestion>of() : result.questions()) {
            LmsQuestionBankService.AiDraft draft = question == null || drafts.size() >= request.questionCount() ? null
                    : toDraft(question, request, knowledge, withTeacher ? teacher : null, sent, labels);
            if (draft == null) {
                leftOut++;
            } else {
                drafts.add(draft);
            }
        }
        List<LmsQuestion> added = drafts.isEmpty() ? List.of() : bank.addAiDrafts(accountId, lmsCourseId, drafts);
        leftOut += drafts.size() - added.size();
        return new AiQuestionBankDraftResponse(added.size(), dropped, repeats, leftOut, added.isEmpty() && dropped == 0 && repeats == 0,
                added.stream().map(LmsQuestion::getId).toList());
    }

    private List<ExistingQuestion> existing(Long lmsCourseId) {
        return bank.forQualityChecks(lmsCourseId, MAX_EXISTING).stream()
                .map(question -> new ExistingQuestion(question.id(), question.text().length() > 2000
                        ? question.text().substring(0, 2000) : question.text(), question.inFinalPaper()))
                .toList();
    }

    private static LmsQuestionAiChecks checks(QualityChecks checks, java.util.Set<Long> sent) {
        if (checks == null) {
            return LmsQuestionAiChecks.none();
        }
        Long duplicate = checks.duplicateOfQuestionId() != null && sent.contains(checks.duplicateOfQuestionId())
                ? checks.duplicateOfQuestionId() : null;
        return new LmsQuestionAiChecks(duplicate, Boolean.TRUE.equals(checks.inPreviousPaper()),
                Boolean.TRUE.equals(checks.outOfSyllabus()), enumOrNull(QuestionDifficulty.class, checks.suggestedDifficulty()),
                enumOrNull(BloomLevel.class, checks.suggestedBloomLevel()));
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        try {
            return value == null ? null : Enum.valueOf(type, value);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static LmsQuestionBankService.AiDraft toDraft(BankQuestion question, AiQuestionBankDraftRequest request,
            AiCourseKnowledge knowledge, AiTeacherKnowledge teacher, java.util.Set<Long> sent, Labels labels) {
        QuestionType type;
        try {
            type = QuestionType.valueOf(question.questionType());
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return null;
        }
        List<AiCitationResponse> citations = AiResults.citations(question.citations(), knowledge, teacher);
        if (!request.questionTypes().contains(type) || citations.isEmpty() || question.text() == null || question.text().isBlank()) {
            return null;
        }
        List<LmsQuestionContent.Option> options = switch (type) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> question.options() == null ? List.of() : question.options().stream()
                    .filter(option -> option != null && option.text() != null)
                    .map(option -> new LmsQuestionContent.Option(option.text().strip(), Boolean.TRUE.equals(option.correct())))
                    .toList();
            case SHORT_ANSWER -> question.acceptedAnswers() == null ? List.of() : question.acceptedAnswers().stream()
                    .filter(answer -> answer != null && !answer.isBlank())
                    .map(answer -> new LmsQuestionContent.Option(answer.strip(), true)).toList();
            default -> List.of();
        };
        BigDecimal marks = question.suggestedMarks() == null || question.suggestedMarks().signum() <= 0 ? BigDecimal.ONE
                : question.suggestedMarks().min(MAX_MARKS).setScale(2, RoundingMode.HALF_UP);
        LmsQuestionContent content = new LmsQuestionContent(type, question.text().strip(), LmsTextFormat.PLAIN,
                AiResults.text(question.explanation(), LmsQuestion.MAX_EXPLANATION_LENGTH), options,
                type == QuestionType.TRUE_FALSE ? question.trueFalseAnswer() : null,
                type == QuestionType.DESCRIPTIVE ? AiResults.text(question.answerGuidance(), LmsQuestion.MAX_GUIDANCE_LENGTH) : null,
                labels.topic(), request.difficulty(), marks, labels.bloomLevel(), null);
        List<LmsQuestionSource> questionSources = new ArrayList<>();
        for (AiCitationResponse citation : citations) {
            questionSources.add(new LmsQuestionSource(questionSources.size() + 1, citation.sourceType(), citation.sourceId(),
                    citation.title(), citation.fileName(), citation.pageNumber(), citation.slideNumber(), citation.pageId()));
        }
        return new LmsQuestionBankService.AiDraft(content, questionSources, checks(question.checks(), sent), labels.outcomeId());
    }
}
