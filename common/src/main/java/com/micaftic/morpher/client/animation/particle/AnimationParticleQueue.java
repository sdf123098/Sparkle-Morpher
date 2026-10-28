package com.micaftic.morpher.client.animation.particle;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Per-model effects wait for the same animation sample's actual render root. */
public final class AnimationParticleQueue {
    private record Pending(String locator, Consumer<Vector3f> emitter) {}
    private final List<Pending> pending = new ArrayList<>();
    private long sample = Long.MIN_VALUE;
    private boolean drained;

    public static long sampleKey(int tick, float partialTick) {
        return ((long) tick << 32) | (Float.floatToIntBits(partialTick) & 0xffffffffL);
    }

    public synchronized boolean enqueue(long key, String locator, Consumer<Vector3f> emitter) {
        if (sample != key) {
            pending.clear();
            sample = key;
            drained = false;
        }
        if (drained || pending.size() >= 64) return false;
        pending.add(new Pending(locator, emitter));
        return true;
    }

    public synchronized boolean hasPending() {
        return !pending.isEmpty();
    }

    public void drain(long key, Function<String, Vector3f> locatorResolver) {
        List<Pending> effects;
        synchronized (this) {
            if (sample != key) {
                pending.clear();
                return;
            }
            effects = List.copyOf(pending);
            pending.clear();
            drained = true;
        }
        for (Pending effect : effects) {
            Vector3f offset = locatorResolver.apply(effect.locator());
            if (offset != null && offset.isFinite()) effect.emitter().accept(offset);
        }
    }
}
