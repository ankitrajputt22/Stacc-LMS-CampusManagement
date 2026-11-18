package com.stacc.backend.lms.quiz;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.lms.content.LmsContentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsQuizRepository extends JpaRepository<LmsQuiz, Long> {

    List<LmsQuiz> findAllByLmsCourseIdOrderByIdDesc(Long lmsCourseId);

    List<LmsQuiz> findAllByLmsCourseIdAndStatusOrderByIdDesc(Long lmsCourseId, LmsContentStatus status);

    @Query("select quiz.lmsCourse.id from LmsQuiz quiz where quiz.id = :quizId")
    Optional<Long> findLmsCourseIdById(@Param("quizId") Long quizId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quiz from LmsQuiz quiz where quiz.id = :quizId")
    Optional<LmsQuiz> findByIdForUpdate(@Param("quizId") Long quizId);
}
