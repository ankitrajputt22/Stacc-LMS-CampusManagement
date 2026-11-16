package com.stacc.backend.innovation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InnovationIdeaRepository extends JpaRepository<InnovationIdea, Long> {

    @Query("select i from InnovationIdea i where i.submitter.id = :accountId order by i.submittedAt desc, i.id desc")
    List<InnovationIdea> findForSubmitter(@Param("accountId") Long accountId);

    /** Ideas in these statuses, other than the reader's own, oldest first. */
    @Query("select i from InnovationIdea i where i.status in :statuses and i.submitter.id <> :accountId order by i.submittedAt, i.id")
    List<InnovationIdea> findOpenForReviewer(@Param("statuses") Collection<InnovationIdea.Status> statuses, @Param("accountId") Long accountId);

    @Query("select i from InnovationIdea i join fetch i.submitter order by i.submittedAt desc, i.id desc")
    List<InnovationIdea> findAllForOffice();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InnovationIdea i join fetch i.submitter where i.id = :id")
    Optional<InnovationIdea> findByIdForUpdate(@Param("id") Long id);
}
