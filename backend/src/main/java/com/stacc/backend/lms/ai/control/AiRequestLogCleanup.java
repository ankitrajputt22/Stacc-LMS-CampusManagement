package com.stacc.backend.lms.ai.control;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "stacc.ai", name = "enabled", havingValue = "true")
public class AiRequestLogCleanup {

    private final AiRequestLogRepository log;
    private final AiLimitProperties limits;
    private final Clock clock;

    public AiRequestLogCleanup(AiRequestLogRepository log, AiLimitProperties limits, Clock clock) {
        this.log = log;
        this.limits = limits;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public int removeOld() {
        return log.deleteOlderThan(clock.instant().minus(limits.logRetention()));
    }
}
