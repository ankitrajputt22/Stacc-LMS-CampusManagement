package com.stacc.backend.lms.quiz.attempt;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsQuizAnswerRepository extends JpaRepository<LmsQuizAnswer, Long> {

    List<LmsQuizAnswer> findAllByAttemptId(Long attemptId);

    List<LmsQuizAnswer> findAllByAttemptIdIn(Collection<Long> attemptIds);

    Optional<LmsQuizAnswer> findByAttemptIdAndQuizQuestionId(Long attemptId, Long quizQuestionId);

    /**
     * The answer, read with a locking read. Called while the attempt row is locked: a plain read could still see the
     * snapshot from before an answer another request just saved.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select answer from LmsQuizAnswer answer where answer.attempt.id = :attemptId and answer.quizQuestion.id = :quizQuestionId")
    Optional<LmsQuizAnswer> findForUpdate(@Param("attemptId") Long attemptId, @Param("quizQuestionId") Long quizQuestionId);
}
