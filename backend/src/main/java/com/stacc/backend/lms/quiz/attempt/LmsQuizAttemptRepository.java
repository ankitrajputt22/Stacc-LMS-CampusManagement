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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from LmsQuizAttempt attempt join fetch attempt.quiz where attempt.id = :attemptId")
    Optional<LmsQuizAttempt> findByIdForUpdate(@Param("attemptId") Long attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt from LmsQuizAttempt attempt
            where attempt.quiz.id = :quizId and attempt.courseEnrollment.id = :courseEnrollmentId
            order by attempt.attemptNumber
            """)
    List<LmsQuizAttempt> findAllForUpdate(@Param("quizId") Long quizId, @Param("courseEnrollmentId") Long courseEnrollmentId);

    @Query("""
            select attempt from LmsQuizAttempt attempt join fetch attempt.courseEnrollment enrollment
            join fetch enrollment.semesterEnrollment semester join fetch semester.studentProfile student
            join fetch student.userAccount account
            where attempt.quiz.id = :quizId order by account.loginId, attempt.attemptNumber
            """)
    List<LmsQuizAttempt> findAllWithStudentByQuizId(@Param("quizId") Long quizId);

    @Query("""
            select attempt from LmsQuizAttempt attempt join fetch attempt.quiz join fetch attempt.courseEnrollment enrollment
            join fetch enrollment.semesterEnrollment semester join fetch semester.studentProfile student
            join fetch student.userAccount where attempt.id = :attemptId
            """)
    Optional<LmsQuizAttempt> findWithStudentById(@Param("attemptId") Long attemptId);

    @Query("select attempt from LmsQuizAttempt attempt join fetch attempt.quiz quiz where attempt.courseEnrollment.id = :enrollmentId"
            + " and quiz.lmsCourse.id = :lmsCourseId and attempt.status = com.stacc.backend.lms.quiz.attempt.QuizAttemptStatus.IN_PROGRESS"
            + " and quiz.type = com.stacc.backend.lms.quiz.QuizType.GRADED")
    List<LmsQuizAttempt> findInProgressGraded(@Param("enrollmentId") Long enrollmentId, @Param("lmsCourseId") Long lmsCourseId);

    @Query("select attempt.quiz.lmsCourse.id from LmsQuizAttempt attempt where attempt.id = :attemptId")
    Optional<Long> findLmsCourseIdById(@Param("attemptId") Long attemptId);

    List<LmsQuizAttempt> findAllByQuizIdInAndCourseEnrollmentIdIn(Collection<Long> quizIds, Collection<Long> courseEnrollmentIds);

    List<LmsQuizAttempt> findAllByCourseEnrollmentIdAndQuizIdInOrderByAttemptNumberDesc(Long courseEnrollmentId,
            Collection<Long> quizIds);
}
