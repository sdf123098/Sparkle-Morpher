package com.micaftic.morpher.core.architectury.registry.registries;

import java.util.function.Supplier;

/**
 * Stub for com.micaftic.morpher.core.architectury.registry.registries.RegistrySupplier.
 */
public class RegistrySupplier<T> implements Supplier<T> {
    private final T value;

    public RegistrySupplier(T value) {
        this.value = value;
    }

    @Override
    public T get() {
        return value;
    }
}
