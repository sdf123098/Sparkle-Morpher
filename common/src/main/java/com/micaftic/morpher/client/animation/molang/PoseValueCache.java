package com.micaftic.morpher.client.animation.molang;

import java.util.function.Supplier;
import org.joml.Vector3f;

/** Isolate each source before mixing; returned scratch never aliases the committed history. */
public final class PoseValueCache {
    private final Vector3f last, result = new Vector3f();
    private boolean rejected;
    public PoseValueCache(Vector3f initial) { last = new Vector3f(initial); }
    public Vector3f sample(Supplier<Vector3f> expression) {
        long before = EvaluationHealth.sequence();
        rejected = true;
        try {
            Vector3f next = expression.get();
            if (next != null && Float.isFinite(next.x) && Float.isFinite(next.y) && Float.isFinite(next.z)
                    && before == EvaluationHealth.sequence()) { last.set(next); rejected = false; }
        } catch(RuntimeException failure) { EvaluationHealth.invalid(); }
        return result.set(last);
    }
    public boolean rejected() { return rejected; }
}
