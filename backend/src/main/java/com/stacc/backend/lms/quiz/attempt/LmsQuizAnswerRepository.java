package com.stacc.backend.lms.quiz.attempt;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsQuizAnswerRepository extends JpaRepository<LmsQuizAnswer, Long> {

    List<LmsQuizAnswer> findAllByAttemptId(Long attemptId);

    List<LmsQuizAnswer> findAllByAttemptIdIn(Collection<Long> attemptIds);

    Optional<LmsQuizAnswer> findByAttemptIdAndQuizQuestionId(Long attemptId, Long quizQuestionId);
}
