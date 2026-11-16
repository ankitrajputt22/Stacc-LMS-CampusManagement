package com.stacc.backend.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.report.ReportActor;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Area analytics (v0.16.4): live counts per campus area, each section only for holders of that area's own permission
 * (a missing permission leaves the section out, never shows zero). Rates follow {@link AnalyticsRules}: the event
 * attendance rate needs at least five registrations and the average helpdesk rating at least five ratings. AI usage is
 * the state of course knowledge indexes only, because AI requests are never stored.
 */
@Service
@Transactional(readOnly = true)
public class AreaAnalyticsService {

    public record HostelArea(long bedsInService, long bedsUnderMaintenance, long occupiedBeds, BigDecimal occupancyPercent) {
    }

    public record HelpdeskArea(Map<String, Long> ticketsByStatus, long ratedTickets, BigDecimal averageRating) {
    }

    public record EngagementArea(long activeGroups, long activeMemberships, long pastEvents, long comingEvents, long pastRegistrations,
            long checkedIn, BigDecimal attendancePercent) {
    }

    public record PlacementArea(long openOpportunities, Map<String, Long> applicationsByStatus, Map<String, Long> offersByStatus) {
    }

    public record LibraryArea(long activeTitles, long copiesInService, long openLoans, long overdueLoans, long activeHolds) {
    }

    public record AiArea(Map<String, Long> courseIndexesByStatus) {
    }

    public record Areas(
            @Schema(nullable = true, description = "Only with ADMIN and HOSTEL_MANAGE.") HostelArea hostel,
            @Schema(nullable = true, description = "Only with ADMIN and HELPDESK_TICKET_VIEW_ALL.") HelpdeskArea helpdesk,
            @Schema(nullable = true, description = "Only with ADMIN and CAMPUS_ENGAGEMENT_MANAGE.") EngagementArea engagement,
            @Schema(nullable = true, description = "Only with ADMIN or PLACEMENT_OFFICER and CAREER_MANAGE.") PlacementArea placement,
            @Schema(nullable = true, description = "Only with LIBRARIAN and LIBRARY_MANAGE.") LibraryArea library,
            @Schema(nullable = true, description = "Only with ADMIN.") AiArea ai,
            String note) {
    }

    private final AreaAnalyticsQueries queries;
    private final Clock clock;

    public AreaAnalyticsService(AreaAnalyticsQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    public Areas forActor(ReportActor actor) {
        Instant now = clock.instant();
        HostelArea hostel = null;
        if (actor.holdsWith("ADMIN", "HOSTEL_MANAGE")) {
            long beds = queries.countBedsInService();
            long occupied = queries.countActiveAllocations();
            hostel = new HostelArea(beds, queries.countBedsUnderMaintenance(), occupied, AnalyticsRules.percent(occupied, beds));
        }
        HelpdeskArea helpdesk = null;
        if (actor.holdsWith("ADMIN", "HELPDESK_TICKET_VIEW_ALL")) {
            List<Integer> ratings = queries.findRatings();
            helpdesk = new HelpdeskArea(byStatus(queries.countTicketsByStatus()), ratings.size(), averageOfAtLeastFive(ratings));
        }
        EngagementArea engagement = null;
        if (actor.holdsWith("ADMIN", "CAMPUS_ENGAGEMENT_MANAGE")) {
            Object[] past = first(queries.countPastRegistrations(now));
            long registered = number(past[0]);
            long attended = number(past[1]);
            engagement = new EngagementArea(queries.countActiveGroups(), queries.countActiveMemberships(), queries.countPastEvents(now),
                    queries.countComingEvents(now), registered, attended, AnalyticsRules.classPercent(attended, registered, registered));
        }
        PlacementArea placement = null;
        if (actor.holdsWith("ADMIN", "CAREER_MANAGE") || actor.holdsWith("PLACEMENT_OFFICER", "CAREER_MANAGE")) {
            placement = new PlacementArea(queries.countOpenOpportunities(), byStatus(queries.countApplicationsByStatus()),
                    byStatus(queries.countOffersByStatus()));
        }
        LibraryArea library = null;
        if (actor.holdsWith("LIBRARIAN", "LIBRARY_MANAGE")) {
            Object[] loans = first(queries.countOpenLoans(now));
            library = new LibraryArea(queries.countActiveTitles(), queries.countCopiesInService(), number(loans[0]), number(loans[1]),
                    queries.countActiveHolds());
        }
        AiArea ai = actor.roles().contains("ADMIN") ? new AiArea(byStatus(queries.countKnowledgeIndexesByStatus())) : null;
        return new Areas(hostel, helpdesk, engagement, placement, library, ai, AnalyticsRules.SMALL_GROUP);
    }

    private static BigDecimal averageOfAtLeastFive(List<Integer> ratings) {
        if (ratings.size() < AnalyticsRules.MIN_GROUP) {
            return null;
        }
        return BigDecimal.valueOf(ratings.stream().mapToInt(Integer::intValue).sum()).divide(BigDecimal.valueOf(ratings.size()), 1, RoundingMode.HALF_UP);
    }

    private static Map<String, Long> byStatus(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> String.valueOf(row[0]), row -> number(row[1]), Long::sum, java.util.TreeMap::new));
    }

    private static Object[] first(List<Object[]> rows) {
        return rows.isEmpty() ? new Object[] {0L, 0L} : rows.getFirst();
    }

    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
