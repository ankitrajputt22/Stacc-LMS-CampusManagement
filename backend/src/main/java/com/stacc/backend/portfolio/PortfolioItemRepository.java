package com.stacc.backend.portfolio;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {

    @Query("select i from PortfolioItem i left join fetch i.verifier where i.owner.id = :accountId and i.hiddenAt is null order by case when i.occurredOn is null then 1 else 0 end, i.occurredOn desc, i.id desc")
    List<PortfolioItem> findVisibleForOwner(@Param("accountId") Long accountId);

    /** Items waiting for this faculty member's verification, with their owners. */
    @Query("""
            select i from PortfolioItem i join fetch i.owner where i.verifier.id = :accountId
            and i.verification = com.stacc.backend.portfolio.PortfolioItem.Verification.REQUESTED and i.hiddenAt is null
            order by i.requestedAt, i.id
            """)
    List<PortfolioItem> findWaitingFor(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PortfolioItem i join fetch i.owner left join fetch i.verifier where i.id = :id")
    Optional<PortfolioItem> findByIdForUpdate(@Param("id") Long id);
}
