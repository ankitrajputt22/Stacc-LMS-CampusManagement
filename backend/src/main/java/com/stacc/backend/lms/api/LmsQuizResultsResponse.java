package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttempt;
import com.stacc.backend.lms.quiz.attempt.LmsQuizGradingService;
import com.stacc.backend.lms.quiz.attempt.QuizAnswerOutcome;
import com.stacc.backend.lms.quiz.attempt.QuizAttemptState;
import com.stacc.backend.lms.quiz.attempt.QuizAttemptStatus;
import com.stacc.backend.lms.quiz.attempt.QuizScoring;
import io.swagger.v3.oas.annotations.media.Schema;

/** The attempts at a quiz with their marks, for the faculty who teach the course. Students by college ID only. */
public record LmsQuizResultsResponse(Long quizId, String title, BigDecimal totalMarks, List<Row> attempts) {

    public record Row(
            Long attemptId,
            @Schema(description = "The student's college ID, the login ID of their account.")
            String studentLoginId,
            int attemptNumber,
            QuizAttemptState state,
            Instant startedAt,
            Instant submittedAt,
            boolean late,
            @Schema(description = "The automatic marks of answers no teacher has marked.")
            BigDecimal automaticMarks,
            @Schema(description = "The teachers' marks.")
            BigDecimal teacherMarks,
            @Schema(description = "The score once every answer that needs a teacher is marked, never below zero; null before.")
            BigDecimal score,
            long needsMarking) {
    }

    static LmsQuizResultsResponse of(LmsQuizGradingService.QuizResults results) {
        LmsQuiz quiz = results.quiz();
        List<Row> rows = new ArrayList<>();
        for (LmsQuizAttempt attempt : results.attempts()) {
            QuizScoring.AttemptScore score = results.scores().get(attempt.getId());
            rows.add(new Row(attempt.getId(),
                    attempt.getCourseEnrollment().getSemesterEnrollment().getStudentProfile().getUserAccount().getLoginId(),
                    attempt.getAttemptNumber(), attempt.stateAt(results.now()), attempt.getStartedAt(), attempt.getSubmittedAt(),
                    attempt.isLate(), score.automaticMarks(), score.teacherMarks(),
                    attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS ? null : score.score(),
                    score.count(QuizAnswerOutcome.NEEDS_MARKING)));
        }
        return new LmsQuizResultsResponse(quiz.getId(), quiz.getTitle(), results.totalMarks(), List.copyOf(rows));
    }
}
