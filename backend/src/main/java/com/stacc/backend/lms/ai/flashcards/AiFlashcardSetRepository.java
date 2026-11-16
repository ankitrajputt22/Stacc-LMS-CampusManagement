package com.stacc.backend.lms.ai.flashcards;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiFlashcardSetRepository extends JpaRepository<AiFlashcardSet, Long> {

    List<AiFlashcardSet> findByLmsCourseIdAndUserAccountIdOrderByIdDesc(Long lmsCourseId, Long userAccountId);

    long countByLmsCourseIdAndUserAccountId(Long lmsCourseId, Long userAccountId);

    Optional<AiFlashcardSet> findByIdAndLmsCourseIdAndUserAccountId(Long id, Long lmsCourseId, Long userAccountId);

    /** Cards and due cards per set, as (set ID, cards, due). */
    @Query("select card.set.id, count(card), sum(case when card.dueOn <= :today then 1 else 0 end) from AiFlashcard card"
            + " where card.set.id in :setIds group by card.set.id")
    List<Object[]> countCards(@Param("setIds") Collection<Long> setIds, @Param("today") LocalDate today);
}
