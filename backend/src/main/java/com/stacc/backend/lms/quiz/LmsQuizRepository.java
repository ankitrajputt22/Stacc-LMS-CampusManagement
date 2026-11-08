package com.stacc.backend.lms.quiz;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsQuizRepository extends JpaRepository<LmsQuiz, Long> {

    /** Every quiz of the course, archived ones included, newest first. */
    List<LmsQuiz> findAllByLmsCourseIdOrderByIdDesc(Long lmsCourseId);

    /** The course of the quiz, read without loading the quiz, so access is checked before the row is locked. */
    @Query("select quiz.lmsCourse.id from LmsQuiz quiz where quiz.id = :quizId")
    Optional<Long> findLmsCourseIdById(@Param("quizId") Long quizId);

    /** The quiz, locked for a change. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quiz from LmsQuiz quiz where quiz.id = :quizId")
    Optional<LmsQuiz> findByIdForUpdate(@Param("quizId") Long quizId);
}
