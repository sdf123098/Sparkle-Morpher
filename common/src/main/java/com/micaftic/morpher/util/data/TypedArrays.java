package com.micaftic.morpher.util.data;

import org.apache.commons.lang3.tuple.Pair;
import java.util.Collection;

/** Keeps generic-array creation typed cleanly at utility boundaries. */
public final class TypedArrays {
    private TypedArrays() {}

    @SuppressWarnings("unchecked")
    public static <K, V> Pair<K, V>[] pairs(Collection<Pair<K, V>> values) {
        return (Pair<K, V>[]) values.toArray(Pair<?, ?>[]::new);
    }
}
