package com.stacc.backend.lms.ai;

import com.stacc.backend.lms.ai.AiServiceMessages.AskRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.AskResult;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentDraftRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentDraftResult;
import com.stacc.backend.lms.ai.AiServiceMessages.BankDraftRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.BankDraftResult;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeResult;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexResult;
import com.stacc.backend.lms.ai.AiServiceMessages.SummaryRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.SummaryResult;

/**
 * The backend's only way to the internal AI service. Every method throws {@link AiServiceException} instead of
 * returning an unusable result. Callers have already checked who the user is and what they may see.
 */
public interface AiServiceClient {

    /** Whether AI is switched on and configured. When false, every other method throws {@code DISABLED}. */
    boolean isEnabled();

    /** Whether the AI service answers its authenticated health check. Never throws. */
    boolean isReachable();

    ReplaceIndexResult replaceIndex(String courseKey, ReplaceIndexRequest request);

    AskResult ask(String courseKey, AskRequest request);

    /** Removes an index and all its files (v0.13.6), such as a student's notes index after their last note is deleted. */
    void deleteIndex(String courseKey);

    /** Questions in the bank's own types, with keyed ones checked by answering them again (v0.14.1). */
    BankDraftResult questionBankDraft(String courseKey, BankDraftRequest request);

    /** A model answer to a question, with sources (v0.14.5). */
    AiServiceMessages.ModelAnswerResult modelAnswer(String courseKey, AiServiceMessages.ModelAnswerRequest request);

    /** A rubric suggestion whose points add up to the assignment's maximum (v0.14.5). */
    AiServiceMessages.RubricResult rubric(String courseKey, AiServiceMessages.RubricRequest request);

    /** Flashcards with sources (v0.15.3). */
    AiServiceMessages.FlashcardResult flashcards(String courseKey, AiServiceMessages.FlashcardRequest request);

    SummaryResult summarize(String courseKey, SummaryRequest request);

    PracticeResult practice(String courseKey, PracticeRequest request);

    AssessmentDraftResult assessmentDraft(String courseKey, AssessmentDraftRequest request);
}
