package com.micaftic.morpher.core.model.lifecycle;

import net.minecraft.network.chat.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Delivers exactly one local-import result on the client executor, including discarded tasks. */
public final class LocalImportCompletion {
    private final Consumer<Component> callback;
    private final Executor clientExecutor;
    private final Consumer<RuntimeException> callbackFailureHandler;
    private final AtomicBoolean settled = new AtomicBoolean();

    public LocalImportCompletion(Consumer<Component> callback, Executor clientExecutor,
                                 Consumer<RuntimeException> callbackFailureHandler) {
        this.callback = callback;
        this.clientExecutor = clientExecutor;
        this.callbackFailureHandler = callbackFailureHandler;
    }

    public boolean complete(Component result) {
        if (!this.settled.compareAndSet(false, true)) {
            return false;
        }
        if (this.callback == null) {
            return true;
        }
        try {
            this.clientExecutor.execute(() -> {
                try {
                    this.callback.accept(result);
                } catch (RuntimeException failure) {
                    this.callbackFailureHandler.accept(failure);
                }
            });
        } catch (RuntimeException schedulingFailure) {
            this.callbackFailureHandler.accept(schedulingFailure);
        }
        return true;
    }
}
