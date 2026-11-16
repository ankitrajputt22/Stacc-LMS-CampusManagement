package com.stacc.backend.lms.ai.saved;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSavedAnswerRepository extends JpaRepository<AiSavedAnswer, Long> {

    List<AiSavedAnswer> findByLmsCourseIdAndUserAccountIdOrderByIdDesc(Long lmsCourseId, Long userAccountId);

    long countByLmsCourseIdAndUserAccountId(Long lmsCourseId, Long userAccountId);

    Optional<AiSavedAnswer> findByIdAndLmsCourseIdAndUserAccountId(Long id, Long lmsCourseId, Long userAccountId);
}
