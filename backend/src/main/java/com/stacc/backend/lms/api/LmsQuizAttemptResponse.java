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
import com.stacc.backend.lms.quiz.attempt.QuizAttemptState;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One of the student's own attempts: its questions in the attempt's order and the answers saved so far. It never
 * carries which options are correct, the accepted short answers, the true or false answer, guidance, or explanations.
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
        List<Question> questions) {

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
                List.copyOf(questions));
    }
}
