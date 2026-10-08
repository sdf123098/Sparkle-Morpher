package com.micaftic.morpher.resource.gltf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GltfAnimationControllerTest {
    @Test
    void selectsMotionAnimationByAliasAndRestartsOnStateChange() {
        GltfAnimationController controller = new GltfAnimationController(modelWithAnimations("idle", "walk"));

        assertEquals("idle", controller.animationName());
        controller.selectForMotion(0.3f, true, false, false, 4.0f);

        assertEquals(GltfAnimationController.State.WALK, controller.state());
        assertEquals("walk", controller.animationName());
        assertEquals(0.0f, controller.animationTimeSeconds(4.0f));
        assertEquals(0.25f, controller.animationTimeSeconds(4.25f), 0.00001f);
    }

    @Test
    void fallsBackToFirstAnimationWhenStateAliasIsAbsent() {
        GltfAnimationController controller = new GltfAnimationController(modelWithAnimations("idle"));

        controller.selectForMotion(0.3f, true, false, false, 2.0f);

        assertEquals(GltfAnimationController.State.WALK, controller.state());
        assertEquals(0, controller.animationIndex());
        assertEquals("idle", controller.animationName());
    }

    @Test
    void prefersFirstPlayableAnimationOverEmptyPlaceholder() {
        GltfModel.Node node = new GltfModel.Node("root", -1, List.of(), -1, -1,
                new float[]{0, 0, 0}, new float[]{0, 0, 0, 1}, new float[]{1, 1, 1}, null);
        GltfModel.Channel channel = new GltfModel.Channel(0, GltfModel.Channel.Path.TRANSLATION,
                GltfModel.Channel.Interpolation.LINEAR, new float[]{0, 1},
                new float[]{0, 0, 0, 1, 0, 0}, 3);
        List<GltfModel.Animation> animations = List.of(
                new GltfModel.Animation("placeholder", List.of(), 1.0f),
                new GltfModel.Animation("Action", List.of(channel), 1.0f));
        GltfAnimationController controller = new GltfAnimationController(new GltfModel(null,
                List.of(new GltfModel.Scene("scene", List.of(0))), 0,
                List.of(node), List.of(), List.of(), List.of(), List.of(), List.of(), animations));

        assertEquals(1, controller.animationIndex());
        assertEquals("Action", controller.animationName());
    }

    @Test
    void usesRunWhenModelHasNoWalkAnimation() {
        GltfAnimationController controller = new GltfAnimationController(
                modelWithAnimations("idle", "run", "chuchang", "dianchuo"));

        controller.selectForMotion(0.1f, true, false, false, 3.0f);

        assertEquals(GltfAnimationController.State.WALK, controller.state());
        assertEquals("run", controller.animationName());
        assertEquals(0.25f, controller.animationTimeSeconds(3.25f), 0.00001f);
    }

    @Test
    void mapsAttackAndUseToCustomActionAnimation() {
        GltfAnimationController controller = new GltfAnimationController(
                modelWithAnimations("idle", "run", "dianchuo"));

        controller.selectForMotion(0.0f, true, false, false, true, false, 5.0f);
        assertEquals(GltfAnimationController.State.ATTACK, controller.state());
        assertEquals("dianchuo", controller.animationName());

        controller.selectForMotion(0.0f, true, false, false, false, true, 5.5f);
        assertEquals(GltfAnimationController.State.USE, controller.state());
        assertEquals("dianchuo", controller.animationName());
    }

    @Test
    void clampsNonFiniteClockAndSpeed() {
        GltfAnimationController controller = new GltfAnimationController(modelWithAnimations("idle", "walk"));
        controller.setSpeed(Float.NaN);

        assertEquals(0.0f, controller.animationTimeSeconds(Float.NaN));
        assertTrue(Float.isFinite(controller.animationTimeSeconds(Float.POSITIVE_INFINITY)));
    }

    private static GltfModel modelWithAnimations(String... names) {
        GltfModel.Node node = new GltfModel.Node("root", -1, List.of(), -1, -1,
                new float[]{0, 0, 0}, new float[]{0, 0, 0, 1}, new float[]{1, 1, 1}, null);
        List<GltfModel.Animation> animations = java.util.Arrays.stream(names)
                .map(name -> new GltfModel.Animation(name, List.of(), 1.0f))
                .toList();
        return new GltfModel(null,
                List.of(new GltfModel.Scene("scene", List.of(0))), 0,
                List.of(node), List.of(), List.of(), List.of(), List.of(), List.of(), animations);
    }
}
