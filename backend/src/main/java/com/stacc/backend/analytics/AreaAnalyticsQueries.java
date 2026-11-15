package com.stacc.backend.analytics;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.hostel.HostelBed;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only counts for the area analytics; each is one bounded aggregate query over the area's own records. */
public interface AreaAnalyticsQueries extends Repository<HostelBed, Long> {

    // --- Hostel

    @Query("select count(bed) from HostelBed bed where bed.active = true and bed.underMaintenance = false")
    long countBedsInService();

    @Query("select count(bed) from HostelBed bed where bed.active = true and bed.underMaintenance = true")
    long countBedsUnderMaintenance();

    @Query("select count(allocation) from HostelAllocation allocation where allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE")
    long countActiveAllocations();

    // --- Helpdesk

    @Query("select cast(ticket.status as string), count(ticket) from HelpdeskTicket ticket group by ticket.status")
    List<Object[]> countTicketsByStatus();

    @Query("select ticket.rating from HelpdeskTicket ticket where ticket.rating is not null")
    List<Integer> findRatings();

    // --- Campus engagement

    @Query("select count(campusGroup) from CampusGroup campusGroup where campusGroup.active = true")
    long countActiveGroups();

    @Query("select count(membership) from CampusGroupMembership membership "
            + "where membership.status = com.stacc.backend.engagement.groups.CampusGroupMembership.Status.ACTIVE")
    long countActiveMemberships();

    @Query("select count(event) from CampusEvent event where event.status = com.stacc.backend.engagement.events.CampusEvent.Status.PUBLISHED "
            + "and event.endsAt < :now")
    long countPastEvents(@Param("now") Instant now);

    @Query("select count(event) from CampusEvent event where event.status = com.stacc.backend.engagement.events.CampusEvent.Status.PUBLISHED "
            + "and event.endsAt >= :now")
    long countComingEvents(@Param("now") Instant now);

    /** Registered places and check-ins of published events that have ended. */
    @Query("""
            select count(registration), sum(case when registration.attendedAt is not null then 1 else 0 end)
            from CampusEventRegistration registration
            where registration.status = com.stacc.backend.engagement.events.CampusEventRegistration.Status.REGISTERED
              and registration.event.status = com.stacc.backend.engagement.events.CampusEvent.Status.PUBLISHED
              and registration.event.endsAt < :now
            """)
    List<Object[]> countPastRegistrations(@Param("now") Instant now);

    // --- Placement

    @Query("select count(opportunity) from CareerOpportunity opportunity where opportunity.status = com.stacc.backend.career.CareerOpportunity.Status.OPEN")
    long countOpenOpportunities();

    @Query("select cast(application.status as string), count(application) from CareerApplication application group by application.status")
    List<Object[]> countApplicationsByStatus();

    @Query("select cast(offer.status as string), count(offer) from CareerOffer offer group by offer.status")
    List<Object[]> countOffersByStatus();

    // --- Library

    @Query("select count(title) from LibraryTitle title where title.active = true")
    long countActiveTitles();

    @Query("select count(copy) from LibraryCopy copy where copy.withdrawnAt is null")
    long countCopiesInService();

    @Query("select count(loan), sum(case when loan.dueAt < :now then 1 else 0 end) from LibraryLoan loan where loan.returnedAt is null")
    List<Object[]> countOpenLoans(@Param("now") Instant now);

    @Query("select count(hold) from LibraryHold hold where hold.status = com.stacc.backend.library.LibraryHold.Status.ACTIVE")
    long countActiveHolds();

    // --- AI

    @Query("select cast(knowledge.status as string), count(knowledge) from AiCourseKnowledgeIndex knowledge group by knowledge.status")
    List<Object[]> countKnowledgeIndexesByStatus();
}
