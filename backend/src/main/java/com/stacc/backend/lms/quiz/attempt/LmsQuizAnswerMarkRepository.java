package com.stacc.backend.lms.quiz.attempt;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsQuizAnswerMarkRepository extends JpaRepository<LmsQuizAnswerMark, Long> {

    List<LmsQuizAnswerMark> findAllByAnswerIdIn(Collection<Long> answerIds);

    Optional<LmsQuizAnswerMark> findByAnswerId(Long answerId);
}
