package com.micaftic.morpher.client.animation.particle;

import com.micaftic.morpher.geckolib3.geo.render.built.GeoBone;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AnimationParticleRuntimeTest {
    @Test
    void inactiveEventsDoNotEvaluateTheirStartupScript() throws Exception {
        var calls = new AtomicInteger();
        com.micaftic.morpher.molang.runtime.Function bump = (context, arguments) -> calls.incrementAndGet();
        var script = com.micaftic.morpher.molang.MolangEngine.fromCustomBinding(
                name -> "bump".equals(name) ? bump : null).parse("bump()").getFirst();
        var arguments = new com.micaftic.morpher.molang.runtime.Function.ArgumentCollection(List.of(
                com.micaftic.morpher.molang.parser.ast.FloatExpression.ZERO,
                com.micaftic.morpher.molang.parser.ast.FloatExpression.ZERO, script));
        var owner = new com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext<net.minecraft.world.entity.Entity>(
                null, null, null, null);
        var evaluator = new com.micaftic.morpher.molang.runtime.ExpressionEvaluatorImpl<
                com.micaftic.morpher.geckolib3.core.molang.context.IContext<net.minecraft.world.entity.Entity>>(owner);
        var function = new com.micaftic.morpher.client.animation.molang.functions.ysm.AnimationParticleFunction();
        assertEquals(false, function.eval(evaluator, arguments));
        assertEquals(0, calls.get());
    }

    @Test
    void repeatedPassesCannotEmitTheSameSampleTwice() {
        var queue = new AnimationParticleQueue();
        var count = new AtomicInteger();
        long key = AnimationParticleQueue.sampleKey(10, .5f);
        assertTrue(queue.enqueue(key, "hand", offset -> count.incrementAndGet()));
        queue.drain(key, name -> new Vector3f());
        queue.drain(key, name -> new Vector3f());
        assertFalse(queue.enqueue(key, "hand", offset -> count.incrementAndGet()));
        assertEquals(1, count.get());
    }

    @Test
    void staleSamplesAreDiscardedAndPendingEffectsAreBounded() {
        var queue = new AnimationParticleQueue();
        var count = new AtomicInteger();
        for (int i = 0; i < 64; i++) assertTrue(queue.enqueue(1, "hand", offset -> count.incrementAndGet()));
        assertFalse(queue.enqueue(1, "hand", offset -> count.incrementAndGet()));
        queue.drain(2, name -> new Vector3f());
        assertEquals(0, count.get());
        assertTrue(queue.enqueue(2, "hand", offset -> count.incrementAndGet()));
        queue.drain(2, name -> new Vector3f());
        assertEquals(1, count.get());
    }

    @Test
    void locatorUsesTheCurrentParentPoseAndTheActualRenderRoot() {
        var root = new GeoBone("root", false, false, false, 0, 0, 0, 0, 0, 0);
        var hand = new GeoBone("hand", false, false, false, 16, 0, 0, 0, 0, 0);
        hand.parentIdx = 0;
        var parameters = new float[24];
        for (int index : new int[]{6, 7, 8, 18, 19, 20}) parameters[index] = 1;
        parameters[2] = (float) Math.PI / 2;
        parameters[3] = -16;
        parameters[6] = parameters[7] = parameters[8] = 2;
        var renderRoot = new Matrix4f().translation(3, 4, 5).scale(2, 3, 4);
        var position = AnimationParticleLocator.resolve(List.of(root, hand), parameters, "hand", renderRoot);
        assertNotNull(position);
        assertEquals(5f, position.x, 1e-5f);
        assertEquals(10f, position.y, 1e-5f);
        assertEquals(5f, position.z, 1e-5f);
        assertNull(AnimationParticleLocator.resolve(List.of(root, hand), parameters, "absent", renderRoot));
    }
}
