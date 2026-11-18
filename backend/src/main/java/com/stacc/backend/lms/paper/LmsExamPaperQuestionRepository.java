package com.stacc.backend.lms.paper;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsExamPaperQuestionRepository extends JpaRepository<LmsExamPaperQuestion, Long> {

    @Query("""
            select distinct link from LmsExamPaperQuestion link join fetch link.question question
            left join fetch question.options left join fetch question.outcome
            where link.paper.id = :paperId order by link.position
            """)
    List<LmsExamPaperQuestion> findAllForPaper(@Param("paperId") Long paperId);

    @Query("select link.question.id from LmsExamPaperQuestion link where link.paper.id = :paperId")
    List<Long> findQuestionIdsForPaper(@Param("paperId") Long paperId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from LmsExamPaperQuestion link where link.paper.id = :paperId")
    void deleteAllForPaper(@Param("paperId") Long paperId);

    @Query("""
            select link.paper.id as paperId, count(link) as questionCount, sum(link.marks) as totalMarks
            from LmsExamPaperQuestion link where link.paper.id in :paperIds group by link.paper.id
            """)
    List<PaperTotals> totalsFor(@Param("paperIds") Collection<Long> paperIds);

    interface PaperTotals {
        Long getPaperId();

        long getQuestionCount();

        BigDecimal getTotalMarks();
    }
}
