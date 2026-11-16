package com.stacc.backend.lms.ai;

import java.math.BigDecimal;
import java.util.List;

/**
 * The JSON exchanged with the internal AI service. Requests carry only an opaque course key (in the address), a
 * content fingerprint, published course content, and the user's question or generation settings: never an account,
 * login ID, profile, grade, attendance, result, or any other personal record. Replies are checked by the services
 * before anything reaches a browser.
 */
public final class AiServiceMessages {

    private AiServiceMessages() {
    }

    /**
     * One source. A topic, teacher document, or note carries its text; a study material carries its file's bytes as
     * Base64. Only topics and study materials name a topic.
     */
    public record IndexSource(
            String sourceType, long sourceId, Long topicId, String title, String text,
            String fileName, String fileType, String contentBase64) {

        static IndexSource topic(long topicId, String title, String text) {
            return new IndexSource("TOPIC", topicId, topicId, title, text, null, null, null);
        }

        /** A text source kept in its own index (v0.13.5, v0.13.6): a teacher document or a student's note. */
        public static IndexSource text(String sourceType, long sourceId, String title, String text) {
            return new IndexSource(sourceType, sourceId, null, title, text, null, null, null);
        }

        static IndexSource material(long materialId, long topicId, String title, String fileName, String fileType,
                String contentBase64) {
            return new IndexSource("STUDY_MATERIAL", materialId, topicId, title, null, fileName, fileType, contentBase64);
        }
    }

    public record ReplaceIndexRequest(String contentFingerprint, List<IndexSource> sources) {
    }

    public record ReplaceIndexResult(Integer indexedSourceCount, Integer skippedSourceCount, Integer chunkCount,
            String contentFingerprint) {
    }

    public record Citation(String sourceType, Long sourceId, Long topicId, String title, String fileName,
            Integer pageNumber, Integer slideNumber) {
    }

    /** One source a request may use (v0.13.2), as the backend allows it at the moment of the request. */
    public record SourceRef(String sourceType, long sourceId) {
    }

    /** Another index searched with the course's own, with its own fingerprint and allowed sources. */
    public record IndexRef(String indexKey, String indexFingerprint, List<SourceRef> allowedSources) {
    }

    /**
     * A question, with up to three earlier turns from the student's open page and a style (v0.15.1). The page sends the
     * turns; nothing is stored.
     */
    public record AskRequest(String indexFingerprint, String question, List<SourceRef> allowedSources,
            List<IndexRef> additionalIndexes, List<Turn> history, String style) {

        public AskRequest(String indexFingerprint, String question, List<SourceRef> allowedSources,
                List<IndexRef> additionalIndexes) {
            this(indexFingerprint, question, allowedSources, additionalIndexes, List.of(), "NORMAL");
        }

        public AskRequest(String indexFingerprint, String question, List<SourceRef> allowedSources) {
            this(indexFingerprint, question, allowedSources, List.of());
        }
    }

    /** An earlier question and answer on the student's open page (v0.15.1). */
    public record Turn(String question, String answer) {
    }

    public record AskResult(String answer, Boolean insufficientContext, List<Citation> citations) {
    }

    /** A topic's summary, or without a topic a summary of the allowed sources (a module or a document), in a style (v0.15.2). */
    public record SummaryRequest(String indexFingerprint, Long topicId, List<SourceRef> allowedSources,
            List<IndexRef> additionalIndexes, String style) {

        public SummaryRequest(String indexFingerprint, long topicId, List<SourceRef> allowedSources) {
            this(indexFingerprint, topicId, allowedSources, List.of(), "BRIEF");
        }
    }

    public record SummaryResult(String summary, List<String> keyPoints, Boolean insufficientContext,
            List<Citation> citations) {
    }

    public record PracticeRequest(String indexFingerprint, int questionCount, Long topicId, List<SourceRef> allowedSources,
            List<IndexRef> additionalIndexes) {

        public PracticeRequest(String indexFingerprint, int questionCount, Long topicId, List<SourceRef> allowedSources) {
            this(indexFingerprint, questionCount, topicId, allowedSources, List.of());
        }
    }

    public record PracticeQuestion(String question, String suggestedAnswer, List<Citation> citations) {
    }

    public record PracticeResult(List<PracticeQuestion> questions, Boolean insufficientContext) {
    }

    public record AssessmentDraftRequest(String indexFingerprint, int questionCount, String difficulty,
            String questionType, String additionalInstruction, List<SourceRef> allowedSources, List<IndexRef> additionalIndexes) {

        public AssessmentDraftRequest(String indexFingerprint, int questionCount, String difficulty, String questionType,
                String additionalInstruction, List<SourceRef> allowedSources) {
            this(indexFingerprint, questionCount, difficulty, questionType, additionalInstruction, allowedSources, List.of());
        }
    }

    public record AssessmentQuestion(String questionType, String prompt, List<String> options, String suggestedAnswer,
            BigDecimal suggestedMarks, List<Citation> citations) {
    }

    public record AssessmentDraftResult(String titleSuggestion, String instructionsSuggestion,
            List<AssessmentQuestion> questions, Boolean insufficientContext) {
    }

    /** The AI service's error body: a stable code and a short message. */
    /** A question bank draft in the bank's own types (v0.14.1). */
    public record BankDraftRequest(String indexFingerprint, int questionCount, List<String> questionTypes, String difficulty,
            Long topicId, String additionalInstruction, List<SourceRef> allowedSources, List<IndexRef> additionalIndexes,
            List<ExistingQuestion> existingQuestions) {
    }

    /** A bank question the drafts are compared with (v0.14.2): only its ID, text, and whether a final paper uses it. */
    public record ExistingQuestion(long id, String text, boolean inFinalPaper) {
    }

    public record QualityChecks(Long duplicateOfQuestionId, Boolean inPreviousPaper, Boolean outOfSyllabus,
            String suggestedDifficulty, String suggestedBloomLevel) {
    }

    public record BankOption(String text, Boolean correct) {
    }

    public record BankQuestion(String questionType, String text, List<BankOption> options, Boolean trueFalseAnswer,
            List<String> acceptedAnswers, String answerGuidance, String explanation, BigDecimal suggestedMarks,
            List<Citation> citations, QualityChecks checks) {
    }

    public record BankDraftResult(List<BankQuestion> questions, Integer droppedForKeyMismatch, Integer droppedAsRepeats,
            Boolean insufficientContext) {
    }

    /** A model answer to a bank question (v0.14.5). */
    public record ModelAnswerRequest(String indexFingerprint, String questionText, String questionType, BigDecimal marks,
            List<SourceRef> allowedSources) {
    }

    public record ModelAnswerResult(String answer, List<String> keyPoints, List<Citation> citations, Boolean insufficientContext) {
    }

    /** A rubric suggestion for an assignment (v0.14.5). */
    public record RubricRequest(String indexFingerprint, String title, String instructions, BigDecimal maxMarks, int criteriaCount,
            List<SourceRef> allowedSources) {
    }

    public record RubricLevel(String title, String description, BigDecimal points) {
    }

    public record RubricCriterion(String title, String description, BigDecimal maxPoints, List<RubricLevel> levels) {
    }

    public record RubricResult(List<RubricCriterion> criteria, List<Citation> citations, Boolean insufficientContext) {
    }

    /** A practice answer to mark (v0.15.4). */
    public record EvaluateRequest(String indexFingerprint, String question, String referenceAnswer, String studentAnswer,
            int maxMarks, String mode, List<SourceRef> allowedSources) {
    }

    public record EvaluateResult(BigDecimal marks, String verdict, String feedback, String followUpQuestion,
            List<Citation> citations, Boolean insufficientContext) {
    }

    /** Flashcards from the allowed sources or one topic (v0.15.3). */
    public record FlashcardRequest(String indexFingerprint, int cardCount, Long topicId, List<SourceRef> allowedSources) {
    }

    public record Flashcard(String front, String back, List<Citation> citations) {
    }

    public record FlashcardResult(List<Flashcard> cards, Boolean insufficientContext) {
    }

    record ErrorBody(String code, String message) {
    }
}
