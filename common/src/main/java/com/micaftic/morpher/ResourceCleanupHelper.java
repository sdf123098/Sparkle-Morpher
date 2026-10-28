package com.micaftic.morpher;

import java.lang.ref.Cleaner;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ResourceCleanupHelper {

    private static final Cleaner CLEANER = Cleaner.create();

    public static <T> void registerCleanup(Object obj, T t, Consumer<T> consumer) {
        CLEANER.register(obj, () -> {
            consumer.accept(t);
        });
    }

    public static <T0, T1> void registerBiCleanup(Object obj, T0 t0, T1 t1, BiConsumer<T0, T1> biConsumer) {
        CLEANER.register(obj, () -> {
            biConsumer.accept(t0, t1);
        });
    }
}