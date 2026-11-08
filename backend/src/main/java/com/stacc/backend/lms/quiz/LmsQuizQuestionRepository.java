package com.stacc.backend.lms.quiz;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsQuizQuestionRepository extends JpaRepository<LmsQuizQuestion, Long> {

    /** The questions of a quiz in order, each with its bank question and that question's options. */
    @Query("""
            select distinct link from LmsQuizQuestion link join fetch link.question question left join fetch question.options
            where link.quiz.id = :quizId order by link.position
            """)
    List<LmsQuizQuestion> findAllForQuiz(@Param("quizId") Long quizId);

    /** The questions of several quizzes, each quiz's in order, with their bank questions and options. */
    @Query("""
            select distinct link from LmsQuizQuestion link join fetch link.question question left join fetch question.options
            where link.quiz.id in :quizIds order by link.quiz.id, link.position
            """)
    List<LmsQuizQuestion> findAllForQuizzes(@Param("quizIds") Collection<Long> quizIds);

    /** The links of a quiz in order, without loading the bank questions. */
    List<LmsQuizQuestion> findAllByQuizIdOrderByPosition(Long quizId);

    /**
     * The same links read with a locking read. Called while the quiz row is locked, so a question another request just
     * added or removed is seen.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select link from LmsQuizQuestion link where link.quiz.id = :quizId order by link.position")
    List<LmsQuizQuestion> findAllForUpdate(@Param("quizId") Long quizId);

    /** How many questions each quiz has and the marks they carry together. */
    @Query("""
            select link.quiz.id as quizId, count(link) as questionCount, sum(link.marks) as totalMarks
            from LmsQuizQuestion link where link.quiz.id in :quizIds group by link.quiz.id
            """)
    List<QuizTotals> totalsFor(@Param("quizIds") Collection<Long> quizIds);

    interface QuizTotals {
        Long getQuizId();

        long getQuestionCount();

        BigDecimal getTotalMarks();
    }
}
