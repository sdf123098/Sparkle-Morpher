package com.micaftic.morpher.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelReloadCoordinatorTest {

    @Test
    void latestRequestWinsAndStaleResultIsNotPublished() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            ModelReloadCoordinator<String> coordinator = new ModelReloadCoordinator<>(worker, Runnable::run);
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondPublished = new CountDownLatch(1);
            List<String> published = new CopyOnWriteArrayList<>();
            AtomicInteger failures = new AtomicInteger();

            coordinator.submit(() -> {
                firstStarted.countDown();
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                return "old";
            }, published::add, error -> failures.incrementAndGet());
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

            coordinator.submit(() -> "latest", value -> {
                published.add(value);
                secondPublished.countDown();
            }, error -> failures.incrementAndGet());
            releaseFirst.countDown();

            assertTrue(secondPublished.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("latest"), published);
            assertEquals(0, failures.get());
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
