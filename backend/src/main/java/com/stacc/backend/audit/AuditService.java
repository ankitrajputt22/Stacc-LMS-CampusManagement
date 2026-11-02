package com.stacc.backend.audit;

import java.time.Clock;

import com.stacc.backend.common.web.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a sensitive action, in the caller's transaction, so the change and its record are saved together or not at
 * all. The details are short plain text chosen by the caller; never pass a password, hash, token, phone number, or
 * email. The request ID is taken from the current request, so a record can be matched with its log lines.
 */
@Service
public class AuditService {

    public static final String USER_ACCOUNT = "USER_ACCOUNT";

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorAccountId, AuditAction action, String targetType, Long targetId, String details) {
        String text = details == null || details.isBlank() ? null : details.strip();
        if (text != null && text.length() > AuditEvent.MAX_DETAILS) {
            text = text.substring(0, AuditEvent.MAX_DETAILS - 1) + "…";
        }
        String requestId = MDC.get(RequestIdFilter.KEY);
        events.save(new AuditEvent(clock.instant(), actorAccountId, action, targetType, targetId, text,
                requestId == null || requestId.length() > 64 ? null : requestId));
    }
}
