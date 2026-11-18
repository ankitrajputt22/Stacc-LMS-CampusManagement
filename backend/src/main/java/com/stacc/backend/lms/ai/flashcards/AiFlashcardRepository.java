package com.stacc.backend.lms.ai.flashcards;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiFlashcardRepository extends JpaRepository<AiFlashcard, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select card from AiFlashcard card join fetch card.set set where card.id = :cardId and set.id = :setId"
            + " and set.lmsCourseId = :lmsCourseId and set.userAccountId = :accountId")
    Optional<AiFlashcard> findForReview(@Param("cardId") Long cardId, @Param("setId") Long setId,
            @Param("lmsCourseId") Long lmsCourseId, @Param("accountId") Long accountId);
}
