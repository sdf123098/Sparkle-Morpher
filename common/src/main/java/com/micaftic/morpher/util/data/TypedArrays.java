package com.micaftic.morpher.util.data;

import com.micaftic.morpher.geckolib3.core.keyframe.event.EventKeyFrame;
import org.apache.commons.lang3.tuple.Pair;
import java.util.Collection;

/** Keeps generic-array creation at the boundary of the legacy animation API. */
public final class TypedArrays {
    private TypedArrays() {}

    // Every element comes from a collection with the exact same type parameters.
    @SuppressWarnings("unchecked")
    public static <T> EventKeyFrame<T>[] events(Collection<EventKeyFrame<T>> values) {
        return (EventKeyFrame<T>[]) values.toArray(EventKeyFrame<?>[]::new);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Pair<K, V>[] pairs(Collection<Pair<K, V>> values) {
        return (Pair<K, V>[]) values.toArray(Pair<?, ?>[]::new);
    }
}
