package com.micaftic.morpher.core.architectury.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * MC 26.x: Fabric event bus implementation replacing Architectury stub.
 */
public class Event<T> {
    private final List<T> handlers = new ArrayList<>();

    public T invoker() {
        throw new UnsupportedOperationException("Generic invokers are not available in the local Fabric event shim");
    }

    public void register(T handler) {
        if (handler != null) {
            handlers.add(handler);
        }
    }

    /** Invoke all registered handlers with a consumer action. */
    @SuppressWarnings("unchecked")
    public void fire(Consumer<T> action) {
        for (T handler : handlers) {
            action.accept(handler);
        }
    }

    /** Invoke handlers returning boolean — returns true if any handler returns true. */
    public boolean fireBoolean(Function<T, Boolean> action) {
        for (T handler : handlers) {
            if (action.apply(handler)) return true;
        }
        return false;
    }

    public boolean hasHandlers() {
        return !handlers.isEmpty();
    }

    public List<T> handlers() {
        return Collections.unmodifiableList(handlers);
    }

    public EventResult fireEventResult(Function<T, EventResult> action) {
        for (T handler : handlers) {
            EventResult result = action.apply(handler);
            if (result != null && result.isFalse()) {
                return result;
            }
        }
        return EventResult.pass();
    }
}
