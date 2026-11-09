package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.QuestionType;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizQuestion;
import com.stacc.backend.lms.quiz.QuizType;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAnswer;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttempt;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttemptService;
import com.stacc.backend.lms.quiz.attempt.QuizAnswerOutcome;
import com.stacc.backend.lms.quiz.attempt.QuizAttemptState;
import com.stacc.backend.lms.quiz.attempt.QuizResultStatus;
import com.stacc.backend.lms.quiz.attempt.QuizScoring;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One of the student's own attempts: its questions in the attempt's order and the answers saved so far. Until its
 * result is released it never carries which options are correct, the accepted short answers, the true or false answer,
 * or explanations; after that only when the quiz shows answers with results. Marking guidance is never sent.
 */
public record LmsQuizAttemptResponse(
        Long id,
        Long quizId,
        Long lmsCourseId,
        String quizTitle,
        QuizType quizType,
        int attemptNumber,
        @Schema(description = "IN_PROGRESS, TIME_UP (still open but past its deadline: no more answers), SUBMITTED, or EXPIRED.")
        QuizAttemptState state,
        Instant startedAt,
        @Schema(description = "When answers stop being taken, by the server's clock; null when there is no limit.")
        Instant deadlineAt,
        Instant submittedAt,
        boolean late,
        @Schema(description = "The server's time when this answer was made, so a timer can allow for the device's clock.")
        Instant serverTime,
        BigDecimal totalMarks,
        @Schema(description = "NOT_HANDED_IN, WAITING_FOR_MARKING, WAITING_FOR_RELEASE, or RELEASED.")
        QuizResultStatus resultStatus,
        @Schema(description = "The result once released; null before.")
        Result result,
        List<Question> questions) {

    /** A released result: the score and counts, and each question's outcome. */
    public record Result(BigDecimal score, BigDecimal totalMarks, long correct, long incorrect, long unanswered,
            List<QuestionResult> questions) {
    }

    /**
     * One question's result. The correct options, accepted answers, true or false answer, and explanation come only
     * when the quiz shows answers with results.
     */
    public record QuestionResult(Long quizQuestionId, QuizAnswerOutcome outcome, BigDecimal marksAwarded, String feedback,
            List<Integer> correctOptions, List<String> acceptedAnswers, Boolean trueFalseAnswer, String explanation) {
    }

    public record Question(
            Long quizQuestionId,
            int number,
            QuestionType type,
            String text,
            LmsTextFormat textFormat,
            BigDecimal marks,
            @Schema(description = "The choices of a single or multiple choice question in this attempt's order; empty otherwise.")
            List<Choice> options,
            @Schema(description = "The answer saved so far, or null.")
            LmsQuizAnswerResponse answer) {
    }

    public record Choice(int position, String text) {
    }

    static LmsQuizAttemptResponse of(LmsQuizAttemptService.AttemptView view) {
        LmsQuizAttempt attempt = view.attempt();
        LmsQuiz quiz = attempt.getQuiz();
        List<Question> questions = new ArrayList<>();
        int number = 1;
        for (LmsQuizQuestion quizQuestion : view.questions()) {
            LmsQuestion question = quizQuestion.getQuestion();
            LmsQuizAnswer answer = view.answers().get(quizQuestion.getId());
            questions.add(new Question(quizQuestion.getId(), number++, question.getType(), question.getText(),
                    question.getTextFormat(), quizQuestion.getMarks(),
                    view.choicesOf(quizQuestion).stream().map(option -> new Choice(option.getPosition(), option.getText())).toList(),
                    answer == null ? null : LmsQuizAnswerResponse.of(answer)));
        }
        return new LmsQuizAttemptResponse(attempt.getId(), quiz.getId(), quiz.getLmsCourse().getId(), quiz.getTitle(),
                quiz.getType(), attempt.getAttemptNumber(), attempt.stateAt(view.now()), attempt.getStartedAt(),
                attempt.getDeadlineAt(), attempt.getSubmittedAt(), attempt.isLate(), view.now(), view.totalMarks(),
                view.resultStatus(), view.resultStatus() == QuizResultStatus.RELEASED ? result(view) : null,
                List.copyOf(questions));
    }

    private static Result result(LmsQuizAttemptService.AttemptView view) {
        QuizScoring.AttemptScore score = view.score();
        boolean showAnswers = view.attempt().getQuiz().isShowAnswers();
        List<QuestionResult> results = score.questions().stream().map(scored -> {
            LmsQuestion question = scored.quizQuestion().getQuestion();
            boolean choice = question.getType() == QuestionType.SINGLE_CHOICE || question.getType() == QuestionType.MULTIPLE_CHOICE;
            return new QuestionResult(scored.quizQuestion().getId(), scored.outcome(), scored.marks(),
                    scored.mark() == null ? null : scored.mark().getFeedback(),
                    showAnswers && choice ? QuizScoring.correctPositions(question).stream().sorted().toList() : null,
                    showAnswers && question.getType() == QuestionType.SHORT_ANSWER
                            ? question.getOptions().stream().map(option -> option.getText()).toList() : null,
                    showAnswers ? question.getTrueFalseAnswer() : null,
                    showAnswers ? question.getExplanation() : null);
        }).toList();
        return new Result(score.score(), score.totalMarks(), score.count(QuizAnswerOutcome.CORRECT),
                score.count(QuizAnswerOutcome.INCORRECT), score.count(QuizAnswerOutcome.UNANSWERED), results);
    }
}
