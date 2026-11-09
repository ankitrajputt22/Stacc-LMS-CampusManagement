package com.stacc.backend.lms.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.lms.quiz.attempt.LmsQuizAnswer;

/** The student's saved answer to one question. */
public record LmsQuizAnswerResponse(Long quizQuestionId, List<Integer> selectedOptions, Boolean trueFalseAnswer, String text,
        Instant answeredAt) {

    static LmsQuizAnswerResponse of(LmsQuizAnswer answer) {
        return new LmsQuizAnswerResponse(answer.getQuizQuestion().getId(), List.copyOf(answer.getSelectedPositions()),
                answer.getTrueFalseAnswer(), answer.getTextAnswer(), answer.getAnsweredAt());
    }
}
