package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizService;
import com.stacc.backend.lms.quiz.QuizType;
import io.swagger.v3.oas.annotations.media.Schema;

/** One quiz in the faculty list of a course. */
public record LmsQuizSummaryResponse(
        Long id,
        String title,
        QuizType type,
        LmsContentStatus status,
        long questionCount,
        @Schema(description = "The marks of all its questions together.", example = "10.00")
        BigDecimal totalMarks,
        Integer timeLimitMinutes,
        Instant availableFrom,
        Instant dueAt,
        Instant availableUntil,
        Integer maxAttempts,
        Instant publishedAt) {

    static LmsQuizSummaryResponse of(LmsQuizService.QuizSummary summary) {
        LmsQuiz quiz = summary.quiz();
        return new LmsQuizSummaryResponse(quiz.getId(), quiz.getTitle(), quiz.getType(), quiz.getStatus(),
                summary.questionCount(), summary.totalMarks(), quiz.getTimeLimitMinutes(), quiz.getAvailableFrom(),
                quiz.getDueAt(), quiz.getAvailableUntil(), quiz.getMaxAttempts(), quiz.getPublishedAt());
    }
}
