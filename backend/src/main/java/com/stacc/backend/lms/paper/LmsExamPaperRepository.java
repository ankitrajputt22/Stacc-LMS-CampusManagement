package com.stacc.backend.lms.paper;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsExamPaperRepository extends JpaRepository<LmsExamPaper, Long> {

    List<LmsExamPaper> findAllByLmsCourseIdOrderByIdDesc(Long lmsCourseId);

    @Query("select paper.lmsCourse.id from LmsExamPaper paper where paper.id = :paperId")
    Optional<Long> findLmsCourseIdById(@Param("paperId") Long paperId);

    /** The paper with its course, offering, and catalog course, for the paper's heading. */
    @Query("""
            select paper from LmsExamPaper paper join fetch paper.lmsCourse lmsCourse join fetch lmsCourse.courseOffering offering
            join fetch offering.course where paper.id = :paperId
            """)
    Optional<LmsExamPaper> findWithCourseById(@Param("paperId") Long paperId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select paper from LmsExamPaper paper join fetch paper.lmsCourse where paper.id = :paperId")
    Optional<LmsExamPaper> findByIdForUpdate(@Param("paperId") Long paperId);
}
