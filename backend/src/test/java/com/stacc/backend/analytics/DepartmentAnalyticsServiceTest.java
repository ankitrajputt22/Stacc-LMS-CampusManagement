package com.stacc.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.stacc.backend.academic.responsibility.DepartmentHeadRepository;
import org.junit.jupiter.api.Test;

/** An account that heads no department gets no department and no query runs for it. */
class DepartmentAnalyticsServiceTest {

    @Test
    void someoneWhoHeadsNoDepartmentSeesNothing() {
        DepartmentHeadRepository heads = mock(DepartmentHeadRepository.class);
        DepartmentAnalyticsQueries queries = mock(DepartmentAnalyticsQueries.class);
        when(heads.findAllActive()).thenReturn(List.of());

        assertThat(new DepartmentAnalyticsService(heads, queries).forHead(7L)).isEmpty();
        verify(queries, never()).countOfferings(anyLong());
    }
}
