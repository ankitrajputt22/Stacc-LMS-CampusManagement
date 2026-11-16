package com.stacc.backend.lms.ai.mastery;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiLearningEvidenceRepository extends JpaRepository<AiLearningEvidence, Long> {

    /** The student's evidence in the course, newest first, at most the most recent few hundred. */
    List<AiLearningEvidence> findTop500ByLmsCourseIdAndUserAccountIdOrderByCreatedAtDescIdDesc(Long lmsCourseId, Long userAccountId);

    @Modifying
    @Query("delete from AiLearningEvidence evidence where evidence.lmsCourseId = :lmsCourseId and evidence.userAccountId = :accountId")
    int deleteForStudent(@Param("lmsCourseId") Long lmsCourseId, @Param("accountId") Long accountId);
}
