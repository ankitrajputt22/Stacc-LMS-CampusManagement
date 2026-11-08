package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionReviewStatus;
import com.stacc.backend.lms.question.QuestionStatus;
import com.stacc.backend.lms.question.QuestionType;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizQuestion;
import com.stacc.backend.lms.quiz.LmsQuizService;
import com.stacc.backend.lms.quiz.QuizType;
import io.swagger.v3.oas.annotations.media.Schema;

/** A quiz with its settings and questions, for the faculty who teach the course. */
public record LmsQuizResponse(
        Long id,
        Long lmsCourseId,
        String title,
        String instructions,
        LmsTextFormat instructionsFormat,
        QuizType type,
        Integer timeLimitMinutes,
        Instant availableFrom,
        Instant dueAt,
        Instant availableUntil,
        Integer maxAttempts,
        boolean shuffleQuestions,
        boolean shuffleOptions,
        @Schema(example = "25.00")
        BigDecimal negativeMarkPercent,
        LmsContentStatus status,
        Instant publishedAt,
        @Schema(description = "The marks of all its questions together.", example = "10.00")
        BigDecimal totalMarks,
        @Schema(description = "The questions in order.")
        List<Question> questions) {

    /** One question of the quiz: its place and marks here, and what the bank says about it. */
    public record Question(
            @Schema(description = "The ID of this question in this quiz, used to re-mark, move, or remove it.")
            Long id,
            int position,
            BigDecimal marks,
            Long questionId,
            QuestionType type,
            String text,
            LmsTextFormat textFormat,
            String topic,
            QuestionDifficulty difficulty,
            @Schema(description = "The marks the bank gives the question.")
            BigDecimal bankMarks,
            QuestionReviewStatus reviewStatus,
            boolean practiceAllowed,
            QuestionStatus status) {
    }

    static LmsQuizResponse of(LmsQuizService.QuizDetail detail) {
        LmsQuiz quiz = detail.quiz();
        return new LmsQuizResponse(quiz.getId(), quiz.getLmsCourse().getId(), quiz.getTitle(), quiz.getInstructions(),
                quiz.getInstructionsFormat(), quiz.getType(), quiz.getTimeLimitMinutes(), quiz.getAvailableFrom(),
                quiz.getDueAt(), quiz.getAvailableUntil(), quiz.getMaxAttempts(), quiz.isShuffleQuestions(),
                quiz.isShuffleOptions(), quiz.getNegativeMarkPercent(), quiz.getStatus(), quiz.getPublishedAt(),
                detail.totalMarks(), detail.questions().stream().map(LmsQuizResponse::question).toList());
    }

    private static Question question(LmsQuizQuestion link) {
        LmsQuestion question = link.getQuestion();
        return new Question(link.getId(), link.getPosition(), link.getMarks(), question.getId(), question.getType(),
                question.getText(), question.getTextFormat(), question.getTopic(), question.getDifficulty(),
                question.getMarks(), question.getReviewStatus(), question.isPracticeAllowed(), question.getStatus());
    }
}
