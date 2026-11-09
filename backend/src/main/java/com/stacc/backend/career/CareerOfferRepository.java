package com.stacc.backend.career;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareerOfferRepository extends JpaRepository<CareerOffer, Long> {

    @Query("select f from CareerOffer f where f.application.id in :ids")
    List<CareerOffer> findAllForApplications(@Param("ids") Collection<Long> applicationIds);

    Optional<CareerOffer> findByApplicationId(Long applicationId);

    /** Whether the student has accepted an offer of a placement, for the one-placement policy. */
    @Query("select count(f) > 0 from CareerOffer f where f.application.applicant.id = :accountId "
            + "and f.status = com.stacc.backend.career.CareerOffer.Status.ACCEPTED "
            + "and f.application.opportunity.kind = com.stacc.backend.career.CareerOpportunity.Kind.PLACEMENT")
    boolean hasAcceptedPlacement(@Param("accountId") Long accountId);
}
