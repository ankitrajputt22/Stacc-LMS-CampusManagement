package com.stacc.backend.lms.ai.control;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits on AI requests (v0.13.7). They hold within one backend instance.
 *
 * @param maxConcurrent   the most AI requests (answers, summaries, practice, drafts) the backend sends at once; more wait
 * @param queueWait       how long a request waits for its turn before answering 429
 * @param logRetention    how long request log rows are kept
 */
@ConfigurationProperties(prefix = "stacc.ai.limits")
public record AiLimitProperties(int maxConcurrent, Duration queueWait, Duration logRetention) {

    public AiLimitProperties {
        if (maxConcurrent == 0) {
            maxConcurrent = 4;
        }
        if (maxConcurrent < 1 || maxConcurrent > 64) {
            throw new IllegalArgumentException("AI_MAX_CONCURRENT_REQUESTS must be between 1 and 64");
        }
        queueWait = queueWait == null || queueWait.isNegative() ? Duration.ofSeconds(60) : queueWait;
        if (queueWait.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("AI_QUEUE_WAIT must be at most 10 minutes");
        }
        logRetention = logRetention == null || logRetention.isNegative() || logRetention.isZero()
                ? Duration.ofDays(90) : logRetention;
    }
}
