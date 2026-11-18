package com.stacc.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class Concurrently {

    public record Outcome<T>(T result, Throwable failure) {

        public boolean succeeded() {
            return failure == null;
        }
    }

    private Concurrently() {
    }

    @SafeVarargs
    public static <T> List<Outcome<T>> run(Callable<T>... calls) throws InterruptedException {
        ExecutorService threads = Executors.newFixedThreadPool(calls.length);
        CountDownLatch ready = new CountDownLatch(calls.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(threads.submit(() -> {
                    ready.countDown();
                    start.await();
                    return call.call();
                }));
            }
            ready.await();
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(new Outcome<>(future.get(60, TimeUnit.SECONDS), null));
                } catch (java.util.concurrent.ExecutionException failure) {
                    outcomes.add(new Outcome<>(null, failure.getCause()));
                } catch (java.util.concurrent.TimeoutException timeout) {
                    throw new AssertionError("A concurrent call did not finish within 60 seconds", timeout);
                }
            }
            return outcomes;
        } finally {
            threads.shutdownNow();
        }
    }
}
