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

public interface AiServiceClient {

    boolean isEnabled();

    boolean isReachable();

    ReplaceIndexResult replaceIndex(String courseKey, ReplaceIndexRequest request);

    AskResult ask(String courseKey, AskRequest request);

    void deleteIndex(String courseKey);

    BankDraftResult questionBankDraft(String courseKey, BankDraftRequest request);

    AiServiceMessages.ModelAnswerResult modelAnswer(String courseKey, AiServiceMessages.ModelAnswerRequest request);

    AiServiceMessages.RubricResult rubric(String courseKey, AiServiceMessages.RubricRequest request);

    AiServiceMessages.EvaluateResult evaluate(String courseKey, AiServiceMessages.EvaluateRequest request);

    AiServiceMessages.FlashcardResult flashcards(String courseKey, AiServiceMessages.FlashcardRequest request);

    SummaryResult summarize(String courseKey, SummaryRequest request);

    PracticeResult practice(String courseKey, PracticeRequest request);

    AssessmentDraftResult assessmentDraft(String courseKey, AssessmentDraftRequest request);
}
