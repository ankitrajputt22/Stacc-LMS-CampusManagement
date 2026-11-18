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

    @Query("""
            select distinct question from LmsQuestion question left join fetch question.outcome
            left join fetch question.options
            where question.lmsCourse.id = :lmsCourseId order by question.id desc
            """)
    List<LmsQuestion> findAllForCourse(@Param("lmsCourseId") Long lmsCourseId);

    @Query("select question.id, question.text from LmsQuestion question where question.lmsCourse.id = :lmsCourseId"
            + " and question.status = com.stacc.backend.lms.question.QuestionStatus.ACTIVE order by question.id desc")
    List<Object[]> findActiveTexts(@Param("lmsCourseId") Long lmsCourseId, org.springframework.data.domain.Pageable page);

    @Query(value = """
            select distinct link.lms_question_id from lms_exam_paper_questions link
            join lms_exam_papers paper on paper.id = link.lms_exam_paper_id
            where paper.lms_course_id = :lmsCourseId and paper.finalized_at is not null
            """, nativeQuery = true)
    List<Long> findIdsInFinalPapers(@Param("lmsCourseId") Long lmsCourseId);

    @Query("select question.lmsCourse.id from LmsQuestion question where question.id = :questionId")
    Optional<Long> findLmsCourseIdById(@Param("questionId") Long questionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select question from LmsQuestion question join fetch question.lmsCourse left join fetch question.outcome
            where question.id = :questionId
            """)
    Optional<LmsQuestion> findByIdForUpdate(@Param("questionId") Long questionId);

    @Query(value = """
            select quiz.status from lms_quiz_questions link join lms_quizzes quiz on quiz.id = link.lms_quiz_id
            where link.lms_question_id = :questionId for share
            """, nativeQuery = true)
    List<String> findQuizStatusesForShare(@Param("questionId") Long questionId);

    @Query(value = """
            select paper.status from lms_exam_paper_questions link join lms_exam_papers paper on paper.id = link.lms_exam_paper_id
            where link.lms_question_id = :questionId for share
            """, nativeQuery = true)
    List<String> findPaperStatusesForShare(@Param("questionId") Long questionId);

    @Query(value = """
            select distinct link.lms_question_id from lms_quiz_questions link join lms_quizzes quiz on quiz.id = link.lms_quiz_id
            where quiz.lms_course_id = :lmsCourseId and quiz.status <> 'DRAFT'
            """, nativeQuery = true)
    List<Long> findIdsInPublishedQuizzes(@Param("lmsCourseId") Long lmsCourseId);

    @Query(value = """
            select exists (select 1 from lms_quiz_questions link join lms_quizzes quiz on quiz.id = link.lms_quiz_id
            where link.lms_question_id = :questionId and quiz.status <> 'DRAFT')
            """, nativeQuery = true)
    long countPublishedQuizUses(@Param("questionId") Long questionId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select question from LmsQuestion question where question.id in :questionIds order by question.id")
    List<LmsQuestion> findAllByIdForShare(@Param("questionIds") Collection<Long> questionIds);
}
