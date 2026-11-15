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
import com.stacc.backend.lms.ai.job.AiJobService;
import com.stacc.backend.lms.ai.AiServiceException;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentDraftRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentDraftResult;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentQuestion;
import com.stacc.backend.lms.ai.AiServiceMessages.IndexRef;
import com.stacc.backend.lms.ai.AiIndexKind;
import com.stacc.backend.lms.ai.control.AiCourseControlService;
import com.stacc.backend.lms.ai.control.AiRequestGate;
import com.stacc.backend.lms.ai.control.AiRequestLogEntry;
import com.stacc.backend.lms.ai.teacher.AiTeacherDocumentService;
import com.stacc.backend.lms.ai.teacher.AiTeacherKnowledge;
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
    private final AiJobService jobs;
    private final AiTeacherDocumentService teacherDocuments;
    private final AiCourseControlService controls;
    private final AiRequestGate gate;

    public AiFacultyService(LmsFacultyAccessService facultyAccess, AiKnowledgeSources sources,
            AiKnowledgeRecords records, AiKnowledgeRefresher refresher, AiServiceClient client, AiJobService jobs,
            AiTeacherDocumentService teacherDocuments, AiCourseControlService controls, AiRequestGate gate) {
        this.controls = controls;
        this.gate = gate;
        this.jobs = jobs;
        this.teacherDocuments = teacherDocuments;
        this.facultyAccess = facultyAccess;
        this.sources = sources;
        this.records = records;
        this.refresher = refresher;
        this.client = client;
    }

    public AiKnowledgeStatusResponse status(Long accountId, Long lmsCourseId) {
        requireAccess(accountId, lmsCourseId);
        AiTeacherKnowledge teacher = teacherDocuments.knowledge(lmsCourseId);
        return courseStatus(lmsCourseId).withTeacherDocuments(teacher.documents().size(),
                teacher.isEmpty() || teacherIndexAnswers(lmsCourseId, teacher))
                .withStudentAi(controls.studentAiEnabled(lmsCourseId));
    }

    private AiKnowledgeStatusResponse courseStatus(Long lmsCourseId) {
        AiCourseKnowledge knowledge = sources.publishedKnowledge(lmsCourseId);
        Optional<AiCourseKnowledgeIndex> index = records.find(lmsCourseId);
        if (index.isEmpty()) {
            return new AiKnowledgeStatusResponse(client.isEnabled(), "NOT_INDEXED", false, null,
                    knowledge.publishedSourceCount(), null, jobs.latestRefresh(lmsCourseId).orElse(null));
        }
        AiCourseKnowledgeIndex row = index.get();
        return new AiKnowledgeStatusResponse(client.isEnabled(), row.getStatus().name(),
                row.isStaleFor(knowledge.fingerprint()), row.getIndexedAt(), knowledge.publishedSourceCount(),
                row.getStatus() == AiKnowledgeIndexStatus.FAILED ? row.getLastError() : null, jobs.latestRefresh(lmsCourseId).orElse(null));
    }

    /** Queues a background refresh of the course's AI knowledge and answers at once. */
    public AiJobService.JobView queueRefresh(Long accountId, Long lmsCourseId) {
        requireAccess(accountId, lmsCourseId);
        return jobs.queueRefresh(accountId, lmsCourseId);
    }

    public AiKnowledgeSyncResponse sync(Long accountId, Long lmsCourseId) {
        requireAccess(accountId, lmsCourseId);
        AiKnowledgeRefresher.Outcome outcome = refresher.refresh(lmsCourseId);
        return new AiKnowledgeSyncResponse(AiKnowledgeIndexStatus.READY.name(), outcome.indexedSourceCount(),
                outcome.skippedSourceCount(), outcome.indexedAt());
    }

    public AiAssessmentDraftResponse assessmentDraft(Long accountId, Long lmsCourseId, AiAssessmentDraftRequest request) {
        return gate.run(AiRequestLogEntry.Kind.ASSESSMENT_DRAFT, accountId, lmsCourseId,
                () -> draft(accountId, lmsCourseId, request), AiAssessmentDraftResponse::insufficientContext);
    }

    /** Turns the course's student AI Study Assistant on or off (v0.13.7) and answers the new state. */
    public boolean changeStudentAi(Long accountId, Long lmsCourseId, boolean enabled) {
        return controls.change(accountId, lmsCourseId, enabled);
    }

    private AiAssessmentDraftResponse draft(Long accountId, Long lmsCourseId, AiAssessmentDraftRequest request) {
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
        // Teacher-only documents join only when their index covers exactly the current ones (v0.13.5).
        AiTeacherKnowledge teacher = teacherDocuments.knowledge(lmsCourseId);
        boolean withTeacher = !teacher.isEmpty() && teacherIndexAnswers(lmsCourseId, teacher);
        List<IndexRef> additional = withTeacher
                ? List.of(new IndexRef(AiCourseKeys.forTeacherDocuments(lmsCourseId), teacher.fingerprint(),
                        teacher.allowedSources()))
                : List.of();
        AssessmentDraftResult result;
        try {
            result = client.assessmentDraft(AiCourseKeys.forLmsCourse(lmsCourseId), new AssessmentDraftRequest(
                    knowledge.fingerprint(), request.questionCount(), request.difficulty().name(),
                    request.questionType().name(), note, knowledge.allowedSources(), additional));
        } catch (AiServiceException exception) {
            throw AiErrors.of(exception, AiErrors.TEACHING_NOT_READY);
        }
        List<AiAssessmentQuestionResponse> questions = questions(result, knowledge, withTeacher ? teacher : null, request);
        if (questions.isEmpty()) {
            return new AiAssessmentDraftResponse(null, null, List.of(), true);
        }
        return new AiAssessmentDraftResponse(AiResults.text(result.titleSuggestion(), AiResults.TITLE_MAX),
                AiResults.text(result.instructionsSuggestion(), AiResults.INSTRUCTIONS_MAX), questions, false);
    }

    /** Only well-formed, cited questions of the requested kind survive, at most as many as were asked for. */
    private static List<AiAssessmentQuestionResponse> questions(AssessmentDraftResult result, AiCourseKnowledge knowledge,
            AiTeacherKnowledge teacher, AiAssessmentDraftRequest request) {
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
            List<AiCitationResponse> citations = AiResults.citations(question.citations(), knowledge, teacher);
            if (!typeFits || prompt == null || answer == null || ("MCQ".equals(type) && options.size() < 2)
                    || marks == null || marks.signum() <= 0 || marks.compareTo(MAX_MARKS) > 0 || citations.isEmpty()) {
                continue;
            }
            accepted.add(new AiAssessmentQuestionResponse(type, prompt, options, answer, marks, citations));
        }
        return List.copyOf(accepted);
    }

    private boolean teacherIndexAnswers(Long lmsCourseId, AiTeacherKnowledge teacher) {
        return records.find(lmsCourseId, AiIndexKind.TEACHER_DOCUMENTS)
                .map(index -> index.answersFor(teacher.fingerprint())).orElse(false);
    }

    private void requireAccess(Long accountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }
}
