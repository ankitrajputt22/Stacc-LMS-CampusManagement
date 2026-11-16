package com.stacc.backend.lms.ai.api;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionBankService;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionReviewStatus;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizService;
import com.stacc.backend.lms.quiz.LmsQuizSettings;
import com.stacc.backend.lms.quiz.QuizType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Quiz drafts (v0.14.3): an ordinary draft quiz filled from reviewed, active bank questions of the chosen types first
 * (offered for practice, for a practice quiz), chosen at random; only when the bank is short, and the teacher allowed
 * it, AI drafts the gap into the bank as Needs review and those questions join the quiz too. Everything goes through
 * the bank's and the quiz's own services and rules, so publishing still needs every question reviewed.
 */
@Service
public class AiQuizDraftService {

    static final int MAX_AI_GAP = 10;
    static final String NOTHING_MATCHES = "No reviewed bank question matches these choices, so no quiz was made.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LmsQuestionBankService bank;
    private final LmsQuizService quizzes;
    private final AiQuestionBankDraftService bankDrafts;

    public AiQuizDraftService(LmsQuestionBankService bank, LmsQuizService quizzes, AiQuestionBankDraftService bankDrafts) {
        this.bank = bank;
        this.quizzes = quizzes;
        this.bankDrafts = bankDrafts;
    }

    public AiQuizDraftResponse draft(Long accountId, Long lmsCourseId, AiQuizDraftRequest request) {
        // The bank's own list checks faculty access first (the same 404 as everywhere else).
        List<LmsQuestion> chosen = new ArrayList<>(bank.list(accountId, lmsCourseId).stream()
                .filter(question -> fits(question, request))
                .toList());
        Collections.shuffle(chosen, RANDOM);
        chosen = new ArrayList<>(chosen.subList(0, Math.min(chosen.size(), request.questionCount())));
        int fromBank = chosen.size();
        int gap = request.questionCount() - fromBank;
        String aiNote = null;
        List<Long> aiQuestionIds = List.of();
        if (gap > 0 && request.fillGapsWithAi()) {
            try {
                AiQuestionBankDraftResponse drafted = bankDrafts.draft(accountId, lmsCourseId, new AiQuestionBankDraftRequest(
                        Math.min(gap, MAX_AI_GAP), request.questionTypes(),
                        request.difficulty() == null ? QuestionDifficulty.MEDIUM : request.difficulty(), null, null));
                aiQuestionIds = drafted.questionIds();
                if (drafted.added() == 0) {
                    aiNote = "AI could not draft questions from the course content.";
                }
            } catch (ApiException refused) {
                aiNote = refused.getMessage();
            }
        }
        if (chosen.isEmpty() && aiQuestionIds.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, aiNote == null ? NOTHING_MATCHES : NOTHING_MATCHES + " " + aiNote);
        }
        LmsQuiz quiz = quizzes.create(accountId, lmsCourseId, new LmsQuizSettings(request.title(), null, LmsTextFormat.PLAIN,
                request.type(), null, null, null, null, null, false, false, null));
        for (LmsQuestion question : chosen) {
            quizzes.addQuestion(accountId, quiz.getId(), question.getId(), null);
        }
        for (Long questionId : aiQuestionIds) {
            quizzes.addQuestion(accountId, quiz.getId(), questionId, null);
        }
        int fromAi = aiQuestionIds.size();
        return new AiQuizDraftResponse(quiz.getId(), fromBank, fromAi, Math.max(0, request.questionCount() - fromBank - fromAi),
                aiNote);
    }

    /** A reviewed, active question of a chosen type, difficulty, and topic, offered for practice for a practice quiz. */
    static boolean fits(LmsQuestion question, AiQuizDraftRequest request) {
        return question.isActive() && question.getReviewStatus() == QuestionReviewStatus.REVIEWED
                && request.questionTypes().contains(question.getType())
                && (request.difficulty() == null || request.difficulty() == question.getDifficulty())
                && (request.topic() == null || request.topic().isBlank() || question.getTopic() != null
                        && question.getTopic().strip().toLowerCase(Locale.ROOT).equals(request.topic().strip().toLowerCase(Locale.ROOT)))
                && (request.type() != QuizType.PRACTICE || question.isPracticeAllowed());
    }
}
