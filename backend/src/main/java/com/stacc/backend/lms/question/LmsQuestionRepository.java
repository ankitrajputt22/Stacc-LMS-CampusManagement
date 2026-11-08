package com.stacc.backend.lms.question;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsQuestionRepository extends JpaRepository<LmsQuestion, Long> {

    /** Every question of the course, archived ones included, newest first, with its outcome and options in one query. */
    @Query("""
            select distinct question from LmsQuestion question left join fetch question.outcome
            left join fetch question.options
            where question.lmsCourse.id = :lmsCourseId order by question.id desc
            """)
    List<LmsQuestion> findAllForCourse(@Param("lmsCourseId") Long lmsCourseId);

    /** The course of the question, read without loading the question, so access is checked before the row is locked. */
    @Query("select question.lmsCourse.id from LmsQuestion question where question.id = :questionId")
    Optional<Long> findLmsCourseIdById(@Param("questionId") Long questionId);

    /** The question, locked for a change, with its course and outcome. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select question from LmsQuestion question join fetch question.lmsCourse left join fetch question.outcome
            where question.id = :questionId
            """)
    Optional<LmsQuestion> findByIdForUpdate(@Param("questionId") Long questionId);

    /**
     * The status of every quiz that uses the question, read with a shared lock. An edit reads this before it locks the
     * question, so a quiz being published waits for the edit, or the edit sees the published quiz and stops.
     */
    @Query(value = """
            select quiz.status from lms_quiz_questions link join lms_quizzes quiz on quiz.id = link.lms_quiz_id
            where link.lms_question_id = :questionId for share
            """, nativeQuery = true)
    List<String> findQuizStatusesForShare(@Param("questionId") Long questionId);

    /** The questions of the course used by a quiz that is published or archived, which can no longer be edited. */
    @Query(value = """
            select distinct link.lms_question_id from lms_quiz_questions link join lms_quizzes quiz on quiz.id = link.lms_quiz_id
            where quiz.lms_course_id = :lmsCourseId and quiz.status <> 'DRAFT'
            """, nativeQuery = true)
    List<Long> findIdsInPublishedQuizzes(@Param("lmsCourseId") Long lmsCourseId);

    /** 1 when a quiz that is published or archived uses the question, otherwise 0. */
    @Query(value = """
            select exists (select 1 from lms_quiz_questions link join lms_quizzes quiz on quiz.id = link.lms_quiz_id
            where link.lms_question_id = :questionId and quiz.status <> 'DRAFT')
            """, nativeQuery = true)
    long countPublishedQuizUses(@Param("questionId") Long questionId);

    /** The questions, locked for reading in ID order, so their latest committed state is seen and kept while publishing. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select question from LmsQuestion question where question.id in :questionIds order by question.id")
    List<LmsQuestion> findAllByIdForShare(@Param("questionIds") Collection<Long> questionIds);
}
