package com.stacc.backend.lms.ai;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiCourseKnowledgeIndexRepository extends JpaRepository<AiCourseKnowledgeIndex, Long> {

    Optional<AiCourseKnowledgeIndex> findByLmsCourseIdAndKind(Long lmsCourseId, AiIndexKind kind);

    @Query("select cast(knowledge.status as string), count(knowledge) from AiCourseKnowledgeIndex knowledge"
            + " where knowledge.kind = :kind group by knowledge.status")
    java.util.List<Object[]> countByStatus(@Param("kind") AiIndexKind kind);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select knowledge from AiCourseKnowledgeIndex knowledge where knowledge.lmsCourse.id = :lmsCourseId"
            + " and knowledge.kind = :kind")
    Optional<AiCourseKnowledgeIndex> findForUpdateByLmsCourseIdAndKind(@Param("lmsCourseId") Long lmsCourseId,
            @Param("kind") AiIndexKind kind);
}
