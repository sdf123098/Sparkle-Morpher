package com.micaftic.morpher.core.model.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GpuCacheTrimCoordinatorTest {
    @Test
    void offThreadRequestDefersStateChangeAndTrimsOnceOnRenderThread() {
        GpuCacheTrimCoordinator<Object> coordinator = new GpuCacheTrimCoordinator<>();
        Object assembly = new Object();
        ArrayDeque<Runnable> renderQueue = new ArrayDeque<>();
        AtomicBoolean onRenderThread = new AtomicBoolean();
        AtomicInteger releases = new AtomicInteger();

        coordinator.request("model", assembly, onRenderThread::get, renderQueue::add,
                candidate -> candidate == assembly, candidate -> releases.incrementAndGet());

        assertEquals(1, renderQueue.size());
        assertEquals(0, releases.get());
        assertFalse(coordinator.isTrimmed("model"));
        onRenderThread.set(true);
        renderQueue.remove().run();

        assertEquals(1, releases.get());
        assertTrue(coordinator.isTrimmed("model", assembly));
        coordinator.request("model", assembly, onRenderThread::get, renderQueue::add,
                candidate -> candidate == assembly, candidate -> releases.incrementAndGet());
        assertEquals(1, releases.get());
    }

    @Test
    void queuedTrimDoesNothingAfterTheAssemblyHasBeenReplaced() {
        GpuCacheTrimCoordinator<Object> coordinator = new GpuCacheTrimCoordinator<>();
        Object oldAssembly = new Object();
        Object newAssembly = new Object();
        Object[] currentAssembly = { oldAssembly };
        ArrayDeque<Runnable> renderQueue = new ArrayDeque<>();
        AtomicBoolean onRenderThread = new AtomicBoolean();
        AtomicInteger releases = new AtomicInteger();

        coordinator.request("model", oldAssembly, onRenderThread::get, renderQueue::add,
                candidate -> currentAssembly[0] == candidate, candidate -> releases.incrementAndGet());
        currentAssembly[0] = newAssembly;
        onRenderThread.set(true);
        renderQueue.remove().run();

        assertEquals(0, releases.get());
        assertFalse(coordinator.isTrimmed("model"));
        coordinator.request("model", newAssembly, onRenderThread::get, renderQueue::add,
                candidate -> currentAssembly[0] == candidate, candidate -> releases.incrementAndGet());
        assertEquals(1, releases.get());
        assertTrue(coordinator.isTrimmed("model", newAssembly));
    }

    @Test
    void failedReleaseCanBeRetried() {
        GpuCacheTrimCoordinator<Object> coordinator = new GpuCacheTrimCoordinator<>();
        Object assembly = new Object();
        AtomicBoolean onRenderThread = new AtomicBoolean(true);

        assertThrows(IllegalStateException.class, () -> coordinator.request("model", assembly,
                onRenderThread::get, Runnable::run, candidate -> true,
                candidate -> { throw new IllegalStateException("release failed"); }));
        assertFalse(coordinator.isTrimmed("model", assembly));

        AtomicInteger releases = new AtomicInteger();
        coordinator.request("model", assembly, onRenderThread::get, Runnable::run,
                candidate -> true, candidate -> releases.incrementAndGet());
        assertEquals(1, releases.get());
        assertTrue(coordinator.isTrimmed("model", assembly));
    }
}
