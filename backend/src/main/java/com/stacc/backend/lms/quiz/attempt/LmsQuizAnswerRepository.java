package com.stacc.backend.lms.quiz.attempt;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsQuizAnswerRepository extends JpaRepository<LmsQuizAnswer, Long> {

    List<LmsQuizAnswer> findAllByAttemptId(Long attemptId);

    Optional<LmsQuizAnswer> findByAttemptIdAndQuizQuestionId(Long attemptId, Long quizQuestionId);
}
