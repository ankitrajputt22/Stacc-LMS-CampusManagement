package com.stacc.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import com.stacc.backend.report.ReportActor;
import org.junit.jupiter.api.Test;

/** Each area appears only with its own permission, and small groups get no average. */
class AreaAnalyticsServiceTest {

    private final AreaAnalyticsQueries queries = mock(AreaAnalyticsQueries.class);
    private final AreaAnalyticsService service = new AreaAnalyticsService(queries, Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), ZoneOffset.UTC));

    @Test
    void aLibrarianSeesOnlyTheLibrary() {
        when(queries.countOpenLoans(org.mockito.ArgumentMatchers.any())).thenReturn(List.<Object[]>of(new Object[] {3L, 1L}));
        AreaAnalyticsService.Areas areas = service.forActor(new ReportActor(1L, Set.of("LIBRARIAN"), Set.of("LIBRARY_MANAGE", "AREA_ANALYTICS_VIEW")));
        assertThat(areas.library().openLoans()).isEqualTo(3);
        assertThat(areas.library().overdueLoans()).isEqualTo(1);
        assertThat(areas.hostel()).isNull();
        assertThat(areas.helpdesk()).isNull();
        assertThat(areas.placement()).isNull();
        assertThat(areas.ai()).isNull();
    }

    @Test
    void fewerThanFiveRatingsGiveNoAverage() {
        when(queries.findRatings()).thenReturn(List.of(5, 4, 5, 3));
        AreaAnalyticsService.Areas areas = service.forActor(new ReportActor(1L, Set.of("ADMIN"), Set.of("HELPDESK_TICKET_VIEW_ALL")));
        assertThat(areas.helpdesk().ratedTickets()).isEqualTo(4);
        assertThat(areas.helpdesk().averageRating()).isNull();
        when(queries.findRatings()).thenReturn(List.of(5, 4, 5, 3, 4));
        assertThat(service.forActor(new ReportActor(1L, Set.of("ADMIN"), Set.of("HELPDESK_TICKET_VIEW_ALL"))).helpdesk().averageRating())
                .isEqualByComparingTo("4.2");
    }
}
