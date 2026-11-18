package com.stacc.backend.lms.ai;

import java.math.BigDecimal;
import java.util.List;

public final class AiServiceMessages {

    private AiServiceMessages() {
    }

    public record IndexSource(
            String sourceType, long sourceId, Long topicId, String title, String text,
            String fileName, String fileType, String contentBase64) {

        static IndexSource topic(long topicId, String title, String text) {
            return new IndexSource("TOPIC", topicId, topicId, title, text, null, null, null);
        }

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

    public record SourceRef(String sourceType, long sourceId) {
    }

    public record IndexRef(String indexKey, String indexFingerprint, List<SourceRef> allowedSources) {
    }

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

    public record Turn(String question, String answer) {
    }

    public record AskResult(String answer, Boolean insufficientContext, List<Citation> citations) {
    }

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

    public record BankDraftRequest(String indexFingerprint, int questionCount, List<String> questionTypes, String difficulty,
            Long topicId, String additionalInstruction, List<SourceRef> allowedSources, List<IndexRef> additionalIndexes,
            List<ExistingQuestion> existingQuestions) {
    }

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

    public record ModelAnswerRequest(String indexFingerprint, String questionText, String questionType, BigDecimal marks,
            List<SourceRef> allowedSources) {
    }

    public record ModelAnswerResult(String answer, List<String> keyPoints, List<Citation> citations, Boolean insufficientContext) {
    }

    public record RubricRequest(String indexFingerprint, String title, String instructions, BigDecimal maxMarks, int criteriaCount,
            List<SourceRef> allowedSources) {
    }

    public record RubricLevel(String title, String description, BigDecimal points) {
    }

    public record RubricCriterion(String title, String description, BigDecimal maxPoints, List<RubricLevel> levels) {
    }

    public record RubricResult(List<RubricCriterion> criteria, List<Citation> citations, Boolean insufficientContext) {
    }

    public record EvaluateRequest(String indexFingerprint, String question, String referenceAnswer, String studentAnswer,
            int maxMarks, String mode, List<SourceRef> allowedSources) {
    }

    public record EvaluateResult(BigDecimal marks, String verdict, String feedback, String followUpQuestion,
            List<Citation> citations, Boolean insufficientContext) {
    }

    public record FlashcardRequest(String indexFingerprint, int cardCount, Long topicId, List<SourceRef> allowedSources) {
    }

    public record Flashcard(String front, String back, List<Citation> citations) {
    }

    public record FlashcardResult(List<Flashcard> cards, Boolean insufficientContext) {
    }

    record ErrorBody(String code, String message) {
    }
}
