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

    /** One published source. A topic carries its text; a study material carries its file's bytes as Base64. */
    public record IndexSource(
            String sourceType, long sourceId, long topicId, String title, String text,
            String fileName, String fileType, String contentBase64) {

        static IndexSource topic(long topicId, String title, String text) {
            return new IndexSource("TOPIC", topicId, topicId, title, text, null, null, null);
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

    public record AskRequest(String indexFingerprint, String question) {
    }

    public record AskResult(String answer, Boolean insufficientContext, List<Citation> citations) {
    }

    public record SummaryRequest(String indexFingerprint, long topicId) {
    }

    public record SummaryResult(String summary, List<String> keyPoints, Boolean insufficientContext,
            List<Citation> citations) {
    }

    public record PracticeRequest(String indexFingerprint, int questionCount, Long topicId) {
    }

    public record PracticeQuestion(String question, String suggestedAnswer, List<Citation> citations) {
    }

    public record PracticeResult(List<PracticeQuestion> questions, Boolean insufficientContext) {
    }

    public record AssessmentDraftRequest(String indexFingerprint, int questionCount, String difficulty,
            String questionType, String additionalInstruction) {
    }

    public record AssessmentQuestion(String questionType, String prompt, List<String> options, String suggestedAnswer,
            BigDecimal suggestedMarks, List<Citation> citations) {
    }

    public record AssessmentDraftResult(String titleSuggestion, String instructionsSuggestion,
            List<AssessmentQuestion> questions, Boolean insufficientContext) {
    }

    /** The AI service's error body: a stable code and a short message. */
    record ErrorBody(String code, String message) {
    }
}
