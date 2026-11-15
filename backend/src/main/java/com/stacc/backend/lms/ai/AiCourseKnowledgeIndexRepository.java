package com.stacc.backend.lms.ai;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiCourseKnowledgeIndexRepository extends JpaRepository<AiCourseKnowledgeIndex, Long> {

    Optional<AiCourseKnowledgeIndex> findByLmsCourseIdAndKind(Long lmsCourseId, AiIndexKind kind);

    /** The row locked for a change, so two refreshes of one index never run at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select knowledge from AiCourseKnowledgeIndex knowledge where knowledge.lmsCourse.id = :lmsCourseId"
            + " and knowledge.kind = :kind")
    Optional<AiCourseKnowledgeIndex> findForUpdateByLmsCourseIdAndKind(@Param("lmsCourseId") Long lmsCourseId,
            @Param("kind") AiIndexKind kind);
}
