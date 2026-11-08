package com.stacc.backend.lms.quiz.attempt;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.lms.content.LmsContentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsQuizAttemptRepository extends JpaRepository<LmsQuizAttempt, Long> {

    /** Who an attempt belongs to and where, read without loading it, so access is checked before the row is locked. */
    @Query("""
            select attempt.courseEnrollment.id as courseEnrollmentId, quiz.id as quizId, quiz.lmsCourse.id as lmsCourseId,
            quiz.status as quizStatus from LmsQuizAttempt attempt join attempt.quiz quiz where attempt.id = :attemptId
            """)
    Optional<AttemptOwner> findOwnerById(@Param("attemptId") Long attemptId);

    interface AttemptOwner {
        Long getCourseEnrollmentId();

        Long getQuizId();

        Long getLmsCourseId();

        LmsContentStatus getQuizStatus();
    }

    /** The attempt, locked for a change. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from LmsQuizAttempt attempt join fetch attempt.quiz where attempt.id = :attemptId")
    Optional<LmsQuizAttempt> findByIdForUpdate(@Param("attemptId") Long attemptId);

    /** One enrollment's attempts at a quiz, locked, in order, so the next number and the attempt limit hold. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt from LmsQuizAttempt attempt
            where attempt.quiz.id = :quizId and attempt.courseEnrollment.id = :courseEnrollmentId
            order by attempt.attemptNumber
            """)
    List<LmsQuizAttempt> findAllForUpdate(@Param("quizId") Long quizId, @Param("courseEnrollmentId") Long courseEnrollmentId);

    /** One enrollment's attempts at these quizzes, newest first. */
    List<LmsQuizAttempt> findAllByCourseEnrollmentIdAndQuizIdInOrderByAttemptNumberDesc(Long courseEnrollmentId,
            Collection<Long> quizIds);
}
