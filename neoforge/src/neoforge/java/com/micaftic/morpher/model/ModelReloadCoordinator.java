package com.micaftic.morpher.model;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Serializes expensive model reloads away from the Minecraft thread. */
public final class ModelReloadCoordinator<T> {

    private final Executor worker;
    private final Executor mainThread;
    private final Object lock = new Object();
    private final AtomicLong generation = new AtomicLong();
    private Request<T> pending;
    private boolean running;

    public ModelReloadCoordinator(Executor worker, Executor mainThread) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    /** Queues one reload and coalesces requests waiting behind an active reload. */
    public long submit(Callable<T> loader, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");

        long requestGeneration = generation.incrementAndGet();
        boolean start;
        synchronized (lock) {
            pending = new Request<>(requestGeneration, loader, onSuccess, onFailure);
            start = !running;
            if (start) {
                running = true;
            }
        }
        if (start) {
            dispatchNext();
        }
        return requestGeneration;
    }

    private void dispatchNext() {
        Request<T> request;
        synchronized (lock) {
            request = pending;
            pending = null;
            if (request == null) {
                running = false;
                return;
            }
        }

        try {
            worker.execute(() -> run(request));
        } catch (RejectedExecutionException error) {
            synchronized (lock) {
                running = false;
            }
            mainThread.execute(() -> request.onFailure.accept(error));
        }
    }

    private void run(Request<T> request) {
        T value = null;
        Throwable failure = null;
        try {
            value = request.loader.call();
        } catch (Throwable error) {
            failure = error;
        }

        T result = value;
        Throwable error = failure;
        mainThread.execute(() -> {
            if (generation.get() != request.generation) {
                return;
            }
            if (error == null) {
                request.onSuccess.accept(result);
            } else {
                request.onFailure.accept(error);
            }
        });

        boolean continueRunning;
        synchronized (lock) {
            continueRunning = pending != null;
            if (!continueRunning) {
                running = false;
            }
        }
        if (continueRunning) {
            dispatchNext();
        }
    }

    private record Request<T>(long generation, Callable<T> loader,
                              Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
    }
}

