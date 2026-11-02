package com.stacc.backend.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.stacc.backend.common.web.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class AuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    private final AuditEventRepository events = mock(AuditEventRepository.class);
    private final AuditService service = new AuditService(events, Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearRequestId() {
        MDC.remove(RequestIdFilter.KEY);
    }

    private AuditEvent saved() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void anEventKeepsWhoWhatWhichWhenAndTheRequest() {
        when(events.save(any())).thenAnswer(call -> call.getArgument(0));
        MDC.put(RequestIdFilter.KEY, "req-123");

        service.record(1L, AuditAction.USER_STATUS_CHANGED, AuditService.USER_ACCOUNT, 57L, "  ACTIVE to DISABLED.  ");

        AuditEvent event = saved();
        assertEquals(NOW, event.getOccurredAt());
        assertEquals(1L, event.getActorAccountId());
        assertEquals(AuditAction.USER_STATUS_CHANGED, event.getAction());
        assertEquals(57L, event.getTargetId());
        assertEquals("ACTIVE to DISABLED.", event.getDetails());
        assertEquals("req-123", event.getRequestId());
    }

    @Test
    void longDetailsAreCutAndBlankOnesAreLeftOut() {
        service.record(null, AuditAction.USER_CREATED, AuditService.USER_ACCOUNT, 57L, "x".repeat(900));
        assertEquals(AuditEvent.MAX_DETAILS, saved().getDetails().length());
    }

    @Test
    void blankDetailsAndAMissingRequestAreStoredAsNothing() {
        service.record(null, AuditAction.USER_CREATED, AuditService.USER_ACCOUNT, 57L, "   ");
        AuditEvent event = saved();
        assertNull(event.getDetails());
        assertNull(event.getRequestId());
    }
}
