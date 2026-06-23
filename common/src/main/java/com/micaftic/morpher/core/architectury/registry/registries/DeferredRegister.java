package com.micaftic.morpher.core.architectury.registry.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.function.Supplier;

/**
 * Stub for com.micaftic.morpher.core.architectury.registry.registries.DeferredRegister.
 */
public class DeferredRegister<T> {
    private final String modId;

    private DeferredRegister(String modId) {
        this.modId = modId;
    }

    public static <T> DeferredRegister<T> create(String modId, ResourceKey<Registry<T>> registryKey) {
        return new DeferredRegister<>(modId);
    }

    public RegistrySupplier<T> register(String name, Supplier<T> supplier) {
        return new RegistrySupplier<>(supplier.get());
    }
}
