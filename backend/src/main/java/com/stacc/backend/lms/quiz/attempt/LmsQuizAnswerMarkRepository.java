package com.stacc.backend.lms.quiz.attempt;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsQuizAnswerMarkRepository extends JpaRepository<LmsQuizAnswerMark, Long> {

    List<LmsQuizAnswerMark> findAllByAnswerIdIn(Collection<Long> answerIds);

    /** The answer's mark, read with a locking read while the attempt row is locked, so a mark just saved is seen. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mark from LmsQuizAnswerMark mark where mark.answer.id = :answerId")
    Optional<LmsQuizAnswerMark> findByAnswerIdForUpdate(@Param("answerId") Long answerId);
}
