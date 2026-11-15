package com.stacc.backend.lms.ai.control;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.ai.AiServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Every AI request a person makes passes here (v0.13.7): it waits its turn in {@link AiRequestLimiter}, and its kind,
 * course, outcome, and duration are written to the request log, never its content or who asked. A course the caller
 * may not use (404) and requests while AI is switched off are not logged.
 */
@Component
public class AiRequestGate {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiRequestGate.class);

    private final AiRequestLimiter limiter;
    private final AiRequestLogRepository log;
    private final AiServiceClient client;
    private final Clock clock;

    public AiRequestGate(AiRequestLimiter limiter, AiRequestLogRepository log, AiServiceClient client, Clock clock) {
        this.limiter = limiter;
        this.log = log;
        this.client = client;
        this.clock = clock;
    }

    public <T> T run(AiRequestLogEntry.Kind kind, Long accountId, Long lmsCourseId, Supplier<T> work, Predicate<T> insufficient) {
        long started = System.nanoTime();
        T result;
        try {
            result = limiter.run(accountId, work);
        } catch (ApiException refused) {
            HttpStatus status = refused.getStatus();
            if (status != HttpStatus.NOT_FOUND && client.isEnabled()) {
                record(kind, lmsCourseId, status == HttpStatus.TOO_MANY_REQUESTS ? AiRequestLogEntry.Outcome.BUSY
                        : status.is5xxServerError() ? AiRequestLogEntry.Outcome.FAILED : AiRequestLogEntry.Outcome.REFUSED, started);
            }
            throw refused;
        } catch (RuntimeException failure) {
            record(kind, lmsCourseId, AiRequestLogEntry.Outcome.FAILED, started);
            throw failure;
        }
        record(kind, lmsCourseId, insufficient.test(result)
                ? AiRequestLogEntry.Outcome.INSUFFICIENT_CONTEXT : AiRequestLogEntry.Outcome.SUCCEEDED, started);
        return result;
    }

    // Saved on its own: AI services hold no transaction, so the row is written whatever the request's outcome.
    private void record(AiRequestLogEntry.Kind kind, Long lmsCourseId, AiRequestLogEntry.Outcome outcome, long started) {
        int millis = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
        try {
            log.save(new AiRequestLogEntry(kind, lmsCourseId, outcome, millis, clock.instant().truncatedTo(ChronoUnit.MICROS)));
        } catch (RuntimeException failure) {
            // The log never decides whether a request works.
            LOGGER.warn("An AI request could not be logged: {}", failure.getClass().getSimpleName());
        }
    }
}
