package com.stacc.backend.lms.ai.control;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AiRequestLimiter {

    public static final String STILL_ANSWERING = "Your previous AI request is still running. Try again when it has finished.";
    public static final String BUSY = "AI is busy right now. Try again in a minute.";

    private final ConcurrentMap<Long, Semaphore> perAccount = new ConcurrentHashMap<>();
    private final Semaphore overall;
    private final int maxConcurrent;
    private final Duration queueWait;

    public AiRequestLimiter(AiLimitProperties limits) {
        this.maxConcurrent = limits.maxConcurrent();
        this.overall = new Semaphore(maxConcurrent, true);
        this.queueWait = limits.queueWait();
    }

    public <T> T run(Long accountId, Supplier<T> work) {
        long deadline = System.nanoTime() + queueWait.toNanos();
        Semaphore mine = perAccount.computeIfAbsent(accountId, id -> new Semaphore(1, true));
        if (!acquire(mine, deadline)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, STILL_ANSWERING);
        }
        try {
            if (!acquire(overall, deadline)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, BUSY);
            }
            try {
                return work.get();
            } finally {
                overall.release();
            }
        } finally {
            mine.release();
        }
    }

    public int running() {
        return maxConcurrent - overall.availablePermits();
    }

    public int waiting() {
        return overall.getQueueLength();
    }

    private static boolean acquire(Semaphore semaphore, long deadline) {
        try {
            return semaphore.tryAcquire(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
