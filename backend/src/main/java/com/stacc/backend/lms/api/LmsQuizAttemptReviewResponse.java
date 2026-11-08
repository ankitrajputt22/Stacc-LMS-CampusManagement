package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.QuestionType;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAnswerMark;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttempt;
import com.stacc.backend.lms.quiz.attempt.LmsQuizGradingService;
import com.stacc.backend.lms.quiz.attempt.QuizAnswerOutcome;
import com.stacc.backend.lms.quiz.attempt.QuizAttemptState;
import com.stacc.backend.lms.quiz.attempt.QuizAttemptStatus;
import com.stacc.backend.lms.quiz.attempt.QuizScoring;
import io.swagger.v3.oas.annotations.media.Schema;

/** One attempt for marking: every question in the quiz's order with its answers, the student's answer, and the marks. */
public record LmsQuizAttemptReviewResponse(
        Long attemptId,
        Long quizId,
        Long lmsCourseId,
        String quizTitle,
        boolean quizPublished,
        String studentLoginId,
        int attemptNumber,
        QuizAttemptState state,
        Instant startedAt,
        Instant deadlineAt,
        Instant submittedAt,
        boolean late,
        BigDecimal negativeMarkPercent,
        BigDecimal totalMarks,
        BigDecimal automaticMarks,
        BigDecimal teacherMarks,
        @Schema(description = "Null until every answer that needs a teacher is marked.")
        BigDecimal score,
        List<Question> questions) {

    public record Question(
            Long quizQuestionId,
            int number,
            QuestionType type,
            String text,
            LmsTextFormat textFormat,
            BigDecimal marks,
            @Schema(description = "Choices with the correct ones marked, or accepted short answers, in the question's order.")
            List<LmsQuestionResponse.Option> options,
            Boolean trueFalseAnswer,
            String answerGuidance,
            String explanation,
            @Schema(description = "The student's answer, or null when unanswered.")
            LmsQuizAnswerResponse answer,
            QuizAnswerOutcome outcome,
            @Schema(description = "The automatic marks; null for a descriptive answer.")
            BigDecimal automaticMarks,
            @Schema(description = "The teacher's marks, or null.")
            Mark mark) {
    }

    public record Mark(BigDecimal marks, String feedback, Instant markedAt) {
    }

    static LmsQuizAttemptReviewResponse of(LmsQuizGradingService.AttemptReview review) {
        LmsQuizAttempt attempt = review.attempt();
        LmsQuiz quiz = attempt.getQuiz();
        QuizScoring.AttemptScore score = review.score();
        List<Question> questions = new ArrayList<>();
        for (QuizScoring.QuestionScore scored : score.questions()) {
            LmsQuestion question = scored.quizQuestion().getQuestion();
            LmsQuizAnswerMark mark = scored.mark();
            questions.add(new Question(scored.quizQuestion().getId(), questions.size() + 1, question.getType(), question.getText(),
                    question.getTextFormat(), scored.quizQuestion().getMarks(),
                    question.getOptions().stream().map(option -> new LmsQuestionResponse.Option(option.getText(), option.isCorrect())).toList(),
                    question.getTrueFalseAnswer(), question.getAnswerGuidance(), question.getExplanation(),
                    scored.answer() == null ? null : LmsQuizAnswerResponse.of(scored.answer()), scored.outcome(),
                    scored.automaticMarks(), mark == null ? null : new Mark(mark.getMarks(), mark.getFeedback(), mark.getMarkedAt())));
        }
        boolean handedIn = attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS;
        return new LmsQuizAttemptReviewResponse(attempt.getId(), quiz.getId(), quiz.getLmsCourse().getId(), quiz.getTitle(),
                quiz.getStatus() == LmsContentStatus.PUBLISHED,
                attempt.getCourseEnrollment().getSemesterEnrollment().getStudentProfile().getUserAccount().getLoginId(),
                attempt.getAttemptNumber(), attempt.stateAt(review.now()), attempt.getStartedAt(), attempt.getDeadlineAt(),
                attempt.getSubmittedAt(), attempt.isLate(), quiz.getNegativeMarkPercent(), score.totalMarks(),
                score.automaticMarks(), score.teacherMarks(), handedIn ? score.score() : null, questions);
    }
}
