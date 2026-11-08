package com.stacc.backend.lms.question;

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
}
