package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.client.animation.molang.functions.physics.IPhysics;
import com.micaftic.morpher.client.animation.molang.functions.physics.FirstOrder;
import com.micaftic.morpher.client.animation.molang.functions.physics.SecondOrder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public class PhysicsManager {

    public static final double RECOVERY_SECONDS = 8.0 / 60.0;
    private static final ThreadLocal<ArrayDeque<Integer>> CURRENT_SCOPE = ThreadLocal.withInitial(ArrayDeque::new);
    private enum Kind { FIRST, SECOND }
    private record Key(Kind kind, long generation, Integer scope, int name) { }
    private static final class Node {
        IPhysics physics;
        long step;
        double time;
        Node(IPhysics physics, long step, double time) {
            this.physics = physics; this.step = step; this.time = time;
        }
    }
    private final Map<Key, Node> physicsValues = new HashMap<>();
    private boolean initialized;
    private double time;
    private long step;
    private long generation;
    private boolean recovery;

    /** Program-owned time domain; solver and node identity remain unchanged. */
    public void updateSimulation(double seconds, boolean recovering) {
        if (!Double.isFinite(seconds)) return;
        if (!initialized) { initialized = true; time = seconds; recovery = true; return; }
        if (seconds == time && !recovering) return;
        time = seconds; recovery = recovering; step++;
    }

    /** Begin an evaluation (ticks, including zero). Integrate only when current arguments arrive. */
    public void update(double renderTicks) {
        if (!Double.isFinite(renderTicks)) return;
        double next = renderTicks / 20.0;
        if (!initialized) { initialized = true; time = next; recovery = true; return; }
        if (Double.compare(next, time) == 0) return;
        recovery = next < time || next - time > RECOVERY_SECONDS;
        time = next;
        step++;
        if ((step & 255) == 0) physicsValues.values().removeIf(n -> time - n.time > 30);
    }

    public float sampleFirst(int name, float input, float response) {
        // First-order names were always shared across bones. Preserve intentional sharing.
        return sample(new Key(Kind.FIRST, generation, null, name), input, response, 0, 0);
    }

    public float sampleSecond(int name, float input, float frequency, float damping, float response) {
        return sample(new Key(Kind.SECOND, generation, CURRENT_SCOPE.get().peek(), name), input, frequency, damping, response);
    }

    private float sample(Key key, float input, float a, float b, float c) {
        Node node = physicsValues.get(key);
        if (node == null) {
            node = new Node(create(key.kind, input, a, b, c), step, time);
            physicsValues.put(key, node);
        } else if (node.step != step) {
            double dt = time - node.time;
            if (recovery || dt < 0 || dt > RECOVERY_SECONDS) {
                if (Float.isFinite(input)) node.physics = create(key.kind, input, a, b, c);
            } else {
                node.physics.setArgs(input, a, b, c);
                node.physics.update((float) dt);
            }
            node.step = step;
            node.time = time;
        }
        return node.physics.getValue();
    }

    private static IPhysics create(Kind kind, float input, float a, float b, float c) {
        return kind == Kind.FIRST ? new FirstOrder(input, a) : new SecondOrder(input, a, b, c);
    }

    public static void pushScope(int scope) { CURRENT_SCOPE.get().push(scope); }
    public static void popScope() {
        ArrayDeque<Integer> scopes = CURRENT_SCOPE.get();
        if (!scopes.isEmpty()) scopes.pop();
        if (scopes.isEmpty()) CURRENT_SCOPE.remove();
    }

    public void clear() {
        initialized = false; time = 0; step = 0; generation++; recovery = false;
        physicsValues.clear();
    }
}
