package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.QuizAvailability;
import com.stacc.backend.lms.quiz.QuizType;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttempt;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttemptService;
import com.stacc.backend.lms.quiz.attempt.QuizAttemptState;
import com.stacc.backend.lms.quiz.attempt.QuizResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** A published quiz as the signed-in student sees it, with their own attempts. Never its questions or answers. */
public record LmsStudentQuizResponse(
        Long id,
        String title,
        String instructions,
        LmsTextFormat instructionsFormat,
        QuizType type,
        Integer timeLimitMinutes,
        Instant availableFrom,
        Instant dueAt,
        Instant availableUntil,
        @Schema(description = "Null means unlimited.")
        Integer maxAttempts,
        BigDecimal negativeMarkPercent,
        long questionCount,
        BigDecimal totalMarks,
        @Schema(description = "NOT_OPEN, OPEN, or CLOSED, by the server's clock.")
        QuizAvailability availability,
        @Schema(description = "Null means unlimited.")
        Integer attemptsLeft,
        @Schema(description = "The attempt still open for answers, to resume; null when none.")
        Long openAttemptId,
        @Schema(description = "Whether Start (or Resume) works now.")
        boolean startable,
        @Schema(description = "The student's attempts, newest first.")
        List<Attempt> attempts) {

    public record Attempt(Long id, int attemptNumber, QuizAttemptState state, Instant startedAt, Instant deadlineAt,
            Instant submittedAt, boolean late,
            @Schema(description = "NOT_HANDED_IN, WAITING_FOR_MARKING, WAITING_FOR_RELEASE, or RELEASED.")
            QuizResultStatus resultStatus,
            @Schema(description = "The score once the result is released; null before.")
            BigDecimal score) {

        static Attempt of(LmsQuizAttempt attempt, LmsQuizAttemptService.StudentQuiz studentQuiz) {
            Instant now = studentQuiz.now();
            return new Attempt(attempt.getId(), attempt.getAttemptNumber(), attempt.stateAt(now), attempt.getStartedAt(),
                    attempt.getDeadlineAt(), attempt.getSubmittedAt(), attempt.isLate(), studentQuiz.resultStatus(attempt),
                    studentQuiz.releasedScore(attempt));
        }
    }

    static LmsStudentQuizResponse of(LmsQuizAttemptService.StudentQuiz studentQuiz) {
        LmsQuiz quiz = studentQuiz.quiz();
        LmsQuizAttempt open = studentQuiz.openAttempt();
        return new LmsStudentQuizResponse(quiz.getId(), quiz.getTitle(), quiz.getInstructions(), quiz.getInstructionsFormat(),
                quiz.getType(), quiz.getTimeLimitMinutes(), quiz.getAvailableFrom(), quiz.getDueAt(), quiz.getAvailableUntil(),
                quiz.getMaxAttempts(), quiz.getNegativeMarkPercent(), studentQuiz.questionCount(), studentQuiz.totalMarks(),
                studentQuiz.availability(), studentQuiz.attemptsLeft(), open == null ? null : open.getId(),
                studentQuiz.startable(),
                studentQuiz.attempts().stream().map(attempt -> Attempt.of(attempt, studentQuiz)).toList());
    }
}
