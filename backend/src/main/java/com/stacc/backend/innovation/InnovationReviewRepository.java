package com.stacc.backend.innovation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InnovationReviewRepository extends JpaRepository<InnovationReview, Long> {

    @Query("select r from InnovationReview r join fetch r.reviewer where r.idea.id in :ideaIds order by r.reviewedAt, r.id")
    List<InnovationReview> findForIdeas(@Param("ideaIds") Collection<Long> ideaIds);

    @Query("select r from InnovationReview r where r.idea.id = :ideaId and r.reviewer.id = :accountId")
    Optional<InnovationReview> findOne(@Param("ideaId") Long ideaId, @Param("accountId") Long accountId);
}
